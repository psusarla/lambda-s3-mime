# lambda-s3-mime

Functionality:
When a new MIME file with attachments is uploaded to  ‘voicechecklist-process-emails’ bucket.
Lambda function will be triggered, it will extract the attachments and upload them to  ‘voicechecklist-attachments’ bucket with a unique file name (UUID). The file metadata will contain From, To and Subject from the MIME file.

To create the jar:
a) You will need maven and Java 8 installed in your workstation.
b) Go to the root folder of the project and run 

mvn clean install

This will create 2 jar files, you should use seslambda-0.0.1-SNAPSHOT.jar file.

Create a Lambda function and make S3 as trigger
