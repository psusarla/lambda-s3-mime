package com.premiumgeek.seslambda;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.junit.Ignore;
import org.junit.Test;

import com.amazonaws.services.s3.model.ObjectMetadata;

public class TestMessageParser {
	
	AttachmentExtractor attachmentExtractor = new AttachmentExtractor();

	@Ignore
	@Test
	public void testEnvironmentVariables() throws Exception {
		String srcBucket = "voicechecklist-process-emails"; 
		String srcKey = "p2g9oj96rnb94s5g54pofstra6od8n2dhdpe1ng1";
		
		
		Map<String, String> environmentVariables = new HashMap<String, String>();
		
		environmentVariables.put("TargetBucket", "voicechecklist-process-emails");
		environmentVariables.put("PrefixWithFilename", "TRUE");
		environmentVariables.put("ExtractBody", "TRUE");	
		
		attachmentExtractor.setEnvironmentVariables(environmentVariables);
		attachmentExtractor.downloadExtractUpload(srcBucket, srcKey);
	}
	
	@Test
	public void testMetaData() {
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.addUserMetadata("from-addresses", "phani@phani.com");

		Map<String, String> environmentVariables = new HashMap<String, String>();
		environmentVariables.put("TargetBucket", "voicechecklist-process-emails");
		environmentVariables.put("PrefixWithFilename", "TRUE");
		environmentVariables.put("ExtractBody", "TRUE");

		attachmentExtractor.setToMetaData(metadata, environmentVariables);

		Map<String, String> updatedMetadata = metadata.getUserMetadata();
		assertEquals(4, updatedMetadata.keySet().size());
	}
	
	@Ignore
	@Test
	public void testEndToEnd() throws Exception {
		String srcBucket = "voicechecklist-process-emails"; 
		String srcKey = "p2g9oj96rnb94s5g54pofstra6od8n2dhdpe1ng1";
		attachmentExtractor.downloadExtractUpload(srcBucket, srcKey);
	}
	
	@Ignore
	@Test
	public void testEndToEndWithEnvVariables() throws Exception {
		Map<String, String> environmentVariables = new HashMap<String, String>();		
		environmentVariables.put("TargetBucket", "voicechecklist-temp");
		environmentVariables.put("PrefixWithFilename", "TRUE");
		environmentVariables.put("ExtractBody", "TRUE");	
		attachmentExtractor.setEnvironmentVariables(environmentVariables);
		
		String srcBucket = "voicechecklist-process-emails"; 
		String srcKey = "dtbae47u303395e9e0n4q2qg5n6ahh8vmn6luo81";
		attachmentExtractor.downloadExtractUpload(srcBucket, srcKey);
	}

	
	@Test
	public void testBodyParser() throws Exception {
		String fileName = "formatted_file_simple";
		InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(fileName);
		String inStr = convertStreamToString(inputStream);
		
		Map<String, String> extractedKeyVals = attachmentExtractor.extractBodyText(inStr);
		assertTrue(extractedKeyVals.size() > 0);
	}

	private String convertStreamToString(java.io.InputStream is) {
		java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
		return s.hasNext() ? s.next() : "";
	}

	@Test
	public void testBodyParser_withjunk() throws Exception {
		String fileName = "formatted_file_withjunk";
		InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(fileName);
		String inStr = convertStreamToString(inputStream);
		
		Map<String, String> extractedKeyVals = attachmentExtractor.extractBodyText(inStr);
		System.out.println(extractedKeyVals);
		assertTrue(extractedKeyVals.size() > 0);
	}

	@Test
	public void testExtractMessage() throws Exception {
		MimeMessage message = loadMessageFromDisk();		
		String bodyText = attachmentExtractor.getTextFromMessage(message);
		System.out.println("Body Text " + bodyText);
	}
	
	@Test
	public void testExtractAndParse() throws Exception {
		MimeMessage message = loadMessageFromDisk();		
		String bodyText = attachmentExtractor.getTextFromMessage(message);
		System.out.println("Body Text " + bodyText);
		Map<String, String> extractedKeyVals = attachmentExtractor.extractBodyText(bodyText);
		System.out.println(extractedKeyVals);
		assertTrue(extractedKeyVals.size() > 0);
	}

	private MimeMessage loadMessageFromDisk() throws MessagingException {
	//	String fileName = "p2g9oj96rnb94s5g54pofstra6od8n2dhdpe1ng1"; //has 1 attachment
    //	String fileName = "qrlp8ahkq46q17jj0vb03chklh6ijdvqqrc4kt01"; //has 1 attachment
		String fileName = "body-and-attachments2";
		InputStream mailFileInputStream = this.getClass().getClassLoader()
                .getResourceAsStream(fileName);
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		MimeMessage message = new MimeMessage(session, mailFileInputStream);
		return message;
	}
	
	@Ignore
	@Test
	public void testDownloadFromS3() throws Exception {
		String srcBucket = "voicechecklist-process-emails";
		String srcKey = "p2g9oj96rnb94s5g54pofstra6od8n2dhdpe1ng1";
		MimeMessage message = attachmentExtractor.downloadFromS3(srcBucket, srcKey);
		assertNotNull(message);
	    assertNotNull(message.getContent());	
	    assertTrue(message.getFrom().length > 0);	
	}

	public void writeToDisk(File inputFile) {
		File file = new File("/Users/phani/workspace/attachments/" + "file1");
		inputFile.renameTo(inputFile);		
		FileOutputStream fop = null;
		String content = "This is the text content";

		try {
			fop = new FileOutputStream(file);

			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}

			// get the content in bytes
			byte[] contentInBytes = content.getBytes();

			fop.write(contentInBytes);
			fop.flush();
			fop.close();

			System.out.println("Done");

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (fop != null) {
					fop.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}