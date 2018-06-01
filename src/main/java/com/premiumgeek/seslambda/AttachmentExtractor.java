package com.premiumgeek.seslambda;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.lang3.StringUtils;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

public class AttachmentExtractor implements RequestHandler<S3EventNotification, String> {
	// This is the destination S3 bucket, attachments will end up
	private String DEFAULT_BUCKET_NAME = "voicechecklist-attachments"; 
	private String bucketName;
	private String prefixFileName;
	private String extractBody;
	private Map<String, String> environmentVariables = new HashMap<String, String>();

	//Used for unit tests
	public Map<String, String> getEnvironmentVariables() {
		return environmentVariables;
	}

	//Used for unit tests
	public void setEnvironmentVariables(Map<String, String> environmentVariables) {
		this.environmentVariables = environmentVariables;
	}

	// This is the main function handling end to end process
	public String handleRequest(S3EventNotification s3EventNotification, Context context) {
		System.out.println("Recieved S3 event notification");
		this.environmentVariables = System.getenv();
		try {
			S3EventNotificationRecord record = s3EventNotification.getRecords().get(0);
			String srcBucket = record.getS3().getBucket().getName(); // Source bucket name
			// Object key may have spaces or unicode non-ASCII characters.
			String srcKey = record.getS3().getObject().getKey().replace('+', ' '); // Source file name
			srcKey = URLDecoder.decode(srcKey, "UTF-8");
			System.out.println("Bucket: " + srcBucket + ", Key: " + srcKey);
			// Below function downloads MIME file, Extracts attachments and
			// Uploads the attachment to destination bucket
			downloadExtractUpload(srcBucket, srcKey);
		} catch (Exception e) {
			System.out.println("Exception thrown " + e.getMessage());
		}
		return "DONE";
	}

	// Downloads MIME file, Extracts attachments and Uploads the attachment to
	// destination bucket
	public void downloadExtractUpload(String srcBucket, String srcKey) throws Exception {
		// AttachmentExtractor attachXtractor = new AttachmentExtractor();
		System.out.println("Downloading MIME from S3");
		setEnvVariables();

		// Mime Message downloaded from source
		MimeMessage message = downloadFromS3(srcBucket, srcKey);
		// Attachments
		List<File> attachments = extractAttachment(message);
		if (attachments.isEmpty()) {
			System.out.println("No attachments found");
			return;
		}
		System.out.println("Number of attachments found: " + attachments.size());

		// Build metadata to be added to the destination attachment file
		ObjectMetadata metadata = buildMetadata(message);
        // if extract body is set
		if (extractBody != null && extractBody.equalsIgnoreCase("TRUE")) {
			//Extract and parse the body
			String bodyText = getTextFromMessage(message);
			Map<String, String> xtractedBdyKeyVals = this.extractBodyText(bodyText);
			setToMetaData(metadata,xtractedBdyKeyVals);
			//metadata.setUserMetadata(xtractedBdyKeyVals);
		}

		// Loop through the attachments
		for (File attachment : attachments) {
			// Upload to S3
			uploadToS3(attachment, metadata);
		}
	}

	protected void setToMetaData(ObjectMetadata metadata, Map<String, String> xtractedBdyKeyVals) {
		if (xtractedBdyKeyVals == null) {
			return;
		}
		
		for (String key: xtractedBdyKeyVals.keySet()) {
			metadata.addUserMetadata(key, xtractedBdyKeyVals.get(key));
		}		
	}

	private void setEnvVariables() {
		bucketName = this.environmentVariables.get("TargetBucket");
		prefixFileName = this.environmentVariables.get("PrefixWithFilename");
		extractBody = this.environmentVariables.get("ExtractBody");

		if (bucketName == null || bucketName.isEmpty())
			bucketName = DEFAULT_BUCKET_NAME;

		System.out.println("bucket name is " + bucketName);
		System.out.println("prefixFileName  is " + prefixFileName);
		System.out.println("extractBody is " + extractBody);
	}

	//Extracts the text - uses recursion 
	protected String getTextFromMessage(Message message) throws Exception {
		String result = "";
		if (message.isMimeType("text/plain")) {
			result = message.getContent().toString();
		} else if (message.isMimeType("multipart/*")) {
			MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
			result = getTextFromMimeMultipart(mimeMultipart);
		}
		return result;
	}

	private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
		String result = "";
		int count = mimeMultipart.getCount();
		for (int i = 0; i < count; i++) {
			BodyPart bodyPart = mimeMultipart.getBodyPart(i);
			if (bodyPart.isMimeType("text/plain")) {
				result = result + "\n" + bodyPart.getContent();
				break; // without break same text appears twice in my tests
			} else if (bodyPart.isMimeType("text/html")) {
				String html = (String) bodyPart.getContent();
				result = result + "\n" + org.jsoup.Jsoup.parse(html).text();
			} else if (bodyPart.getContent() instanceof MimeMultipart) {
				result = result + getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent());
			}
		}
		return result;
	}

	/*
	 * Utility function - Extracts From, To, Subject from source Mime Message -
	 * Builds S3 Metadata object from these properties
	 */
	public ObjectMetadata buildMetadata(MimeMessage message) throws MessagingException {
		String fromAddressList = "";
		int i = 0;
		for (Address address : message.getFrom()) {
			if (i > 0) {
				fromAddressList += ",";
			}
			fromAddressList += address;
			i++;
		}

		String toAddressList = "";
		i = 0;
		for (Address address : message.getAllRecipients()) {
			if (i > 0) {
				toAddressList += ",";
			}
			toAddressList += address;
			i++;
		}

		ObjectMetadata metadata = new ObjectMetadata();
		metadata.addUserMetadata("from-addresses", fromAddressList);
		metadata.addUserMetadata("to-addresses", toAddressList);
		metadata.addUserMetadata("subject", message.getSubject());
		return metadata;
	}

	/*
	 * Downloads the source Mime file and creates a MimeMessage object out of it
	 */
	public MimeMessage downloadFromS3(String srcBucket, String srcKey) throws Exception {
		AmazonS3 s3Client = new AmazonS3Client();
		S3Object s3Object = s3Client.getObject(new GetObjectRequest(srcBucket, srcKey));
		InputStream objectData = s3Object.getObjectContent();
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		MimeMessage message = new MimeMessage(session, objectData);
		return message;
	}

	// Extracts attachments out of the source message
	public List<File> extractAttachment(Message message) throws Exception {
		System.out.println("Parsing the raw MIME");
		List<File> attachments = new ArrayList<File>();
		Multipart multipart = (Multipart) message.getContent();
		for (int i = 0; i < multipart.getCount(); i++) {
			BodyPart bodyPart = multipart.getBodyPart(i);

			if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
				if (StringUtils.isNotBlank(bodyPart.getFileName())) {
					InputStream is = bodyPart.getInputStream();
					 File f = new File("/tmp/" + bodyPart.getFileName());
					FileOutputStream fos = new FileOutputStream(f);
					byte[] buf = new byte[4096];
					int bytesRead;
					while ((bytesRead = is.read(buf)) != -1) {
						fos.write(buf, 0, bytesRead);
					}
					fos.close();
					attachments.add(f);
				}
			}
		}
		return attachments;
	}

	// Uploads the attachment to S3, also sets the metadatas
	public void uploadToS3(File file, ObjectMetadata metadata) throws Exception {
		String keyName = UUID.randomUUID().toString();
		AmazonS3 s3client = new AmazonS3Client();

		System.out.println("prefixFileName:" + prefixFileName);
		if ((prefixFileName != null) && (prefixFileName.equalsIgnoreCase("TRUE"))) {
			keyName = file.getName() + "_" + keyName;
		}
		System.out.println("Uploading attachment to S3 Key: " + keyName);

		PutObjectRequest putObjectReq = new PutObjectRequest(bucketName, keyName, file);
		putObjectReq.setMetadata(metadata);
		s3client.putObject(putObjectReq);
		
	}

	public Map<String, String> extractBodyText(String in) {
		try {
			StringBuilder inputStr = new StringBuilder(in);
			// TODO - handle empty
			int start = inputStr.indexOf("<data>");
			int end = inputStr.indexOf("</data>");
			String extract = inputStr.substring(start + 6, end).trim();
			Properties p = new Properties();
			p.load(new StringReader(extract));
			return (Map) p;
		} catch (Exception e) {
			System.out.println("Exception while extracting body text " + e.getMessage());
		}
		return new HashMap<String, String>();
	}
}