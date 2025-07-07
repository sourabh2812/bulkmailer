package com.ssuman.bulkmail;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SendMail {

    public interface ProgressListener {
        void onStart(int total);
        void onProgress(int sent, int total);
    }

    private static String from, alias, smtpHost, username, password;
    private static int smtpPort, waitTime, batchSize;
    private static boolean isLogEnabled;
    private static final String LOG_FILE = "error.log";
    private static final Object pauseLock = new Object();
    private static final AtomicBoolean paused = new AtomicBoolean(false);
    private static Properties props;

    public static void pause() {
        paused.set(true);
    }

    public static void resume() {
        synchronized (pauseLock) {
            paused.set(false);
            pauseLock.notifyAll();
        }
    }

    public static String sendBulkEmails(String emailFile, String contentFile, String cc, String subject,
                                        String attachmentPath, ProgressListener listener) {
        AtomicReference<String> result = new AtomicReference<>("success");
        final int[] sentCount = {0};
        int batchCounter = 0;

        try {
            loadConfiguration();
            List<String> emailList = new ArrayList<>();
            for (String line : readLines(emailFile)) {
                String[] parts = line.split(",");
                for (String email : parts) {
                    email = email.trim();
                    if (!email.isEmpty()) {
                        emailList.add(email);
                    }
                }
            }

            String emailContent = String.join("\n", readLines(contentFile));
            int total = emailList.size();

            if (listener != null) {
                listener.onStart(total);
            }

            // Create the ExecutorService based on the pool size from properties
            ExecutorService executorService = Executors.newFixedThreadPool(Integer.parseInt(props.getProperty("mail.send.thread.pool.size", "10")));

            // Process emails concurrently
            List<Future<AtomicReference<String>>> futures = new ArrayList<>();
            for (String email : emailList) {
                synchronized (pauseLock) {
                    while (paused.get()) {
                        pauseLock.wait();
                    }
                }

                email = email.trim();
                if (email.isEmpty() || !isValidEmail(email)) {
                    logWarning("Skipping invalid email: " + email);
                    continue;
                }

                // Submit the email sending task to the ExecutorService
                String finalEmail = email;
                Future<AtomicReference<String>> future = executorService.submit(() -> {
                    if (attachmentPath.isEmpty()) {
                        result.set(sendEmail(finalEmail, emailContent, cc, subject));
                    } else {
                        result.set(sendEmailWithAttachment(finalEmail, emailContent, cc, subject, attachmentPath));
                    }

                    // Update progress in a thread-safe manner
                    synchronized (Objects.requireNonNull(listener)) {
                        sentCount[0]++;
                        listener.onProgress(sentCount[0], total);
                    }

                    return result;
                });
                futures.add(future);

                batchCounter++;
                if (batchCounter == batchSize) {
                    Thread.sleep(waitTime);
                    batchCounter = 0;
                }
            }

            // Wait for all email sending tasks to complete
            for (Future<AtomicReference<String>> future : futures) {
                future.get(); // This will block until all emails are sent
            }

            executorService.shutdown(); // Shutdown the executor after completing all tasks

        } catch (Exception e) {
            logError("Error during email sending", e);
            result.set("failure");
        }

        return result.get();
    }

    private static void loadConfiguration() throws IOException {
        props = new Properties();
        File configFile = new File("config.properties");
        if (!configFile.exists()) throw new FileNotFoundException("config.properties not found");

        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
        }

        from = props.getProperty("mail.from");
        alias = props.getProperty("mail.alias");
        smtpHost = props.getProperty("mail.smtp.host");
        smtpPort = Integer.parseInt(props.getProperty("mail.smtp.port", "587"));
        username = props.getProperty("mail.username");
        password = props.getProperty("mail.password");
        waitTime = Integer.parseInt(props.getProperty("mail.wait.time", "900000"));
        batchSize = Integer.parseInt(props.getProperty("mail.send.batch.size", "50"));
        isLogEnabled = Boolean.parseBoolean(props.getProperty("log.enabled", "true"));
    }

    private static List<String> readLines(String filePath) throws IOException {
        return Files.readAllLines(new File(filePath).toPath());
    }

    private static boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    private static String sendEmail(String to, String content, String cc, String subject) {
        try {
            Message msg = prepareMessage(to, cc, subject, content, null);
            Transport.send(msg);
            return "success";
        } catch (Exception e) {
            logError("Failed to send to " + to, e);
            return "failure";
        }
    }

    private static String sendEmailWithAttachment(String to, String content, String cc, String subject, String filePath) {
        try {
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(content, "text/html");

            MimeBodyPart filePart = new MimeBodyPart();
            FileDataSource fds = new FileDataSource(filePath);
            filePart.setDataHandler(new DataHandler(fds));
            filePart.setFileName(fds.getName());

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(filePart);

            Message msg = prepareMessage(to, cc, subject, null, multipart);
            Transport.send(msg);
            return "success";
        } catch (Exception e) {
            logError("Failed to send with attachment to " + to, e);
            return "failure";
        }
    }

    private static Message prepareMessage(String to, String cc, String subject, String content, Multipart multipart)
            throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from, alias));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        if (!cc.isEmpty()) {
            msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
        }
        msg.setSubject(subject);
        msg.setSentDate(new Date());

        if (multipart != null) {
            msg.setContent(multipart);
        } else {
            msg.setContent(content, "text/html; charset=utf-8");
        }

        return msg;
    }

    private static void logWarning(String message) {
        if (isLogEnabled) logToFile("WARNING", message, null);
    }

    private static void logError(String message, Exception e) {
        if (isLogEnabled) logToFile("ERROR", message, e);
    }

    private static void logToFile(String level, String message, Exception e) {
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            out.println("[" + new Date() + "] " + level + ": " + message);
            if (e != null) e.printStackTrace(out);
        } catch (IOException io) {
            io.printStackTrace();
        }
    }
}
