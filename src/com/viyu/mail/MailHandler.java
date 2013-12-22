package com.viyu.mail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class MailHandler {
	public final static String HOST_QQ = "smtp.qq.com";
	public final static String PORT_25 = "25";
	public final static String PORT_465 = "465";
	public final static String PORT_587 = "587";
	public final static String debug_true = "true";
	public final static String debug_false = "false";
	
	private Session session = null;
	private String from = null;
	private String to = null;
	
	public MailHandler(String host, String port, String debug,
			final String userName, final String password,
			String from, String to) {
		Properties properties = new Properties();
		properties.setProperty("mail.smtp.auth", "true");
		properties.setProperty("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");

		properties.setProperty("mail.smtp.host", host);
		properties.setProperty("mail.smtp.port", port);
		properties.setProperty("mail.debug", debug);

		session = Session.getDefaultInstance(properties, new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(userName, password);
			}
		});
		this.from = from;
		this.to = to;
	}

	public void pushContent(String name, String attach) { 
		System.out.println(attach);
		
		try {
			sendMail(from, to, name, attach.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	private boolean sendMail(String from, String to, String name,
			final byte[] attach) {
		try {
			Multipart multiPart = new MimeMultipart();
			MimeBodyPart bodyPart = new MimeBodyPart();
			bodyPart.setDataHandler(new DataHandler(new DataSource() {
				@Override
				public String getContentType() {
					return "application/octet-stream";
				}
				@Override
				public InputStream getInputStream() throws IOException {
					return new ByteArrayInputStream(attach);
				}
				@Override
				public String getName() {
					return "txt file";
				}
				@Override
				public OutputStream getOutputStream() throws IOException {
					return new ByteArrayOutputStream();
				}
			}));
			
			bodyPart.setFileName("Newsmth: " + name+ ".txt");
			multiPart.addBodyPart(bodyPart); 

			Message mailMessage = new MimeMessage(session);
			mailMessage.setFrom(new InternetAddress(from));
			mailMessage.setRecipient(Message.RecipientType.TO,
					new InternetAddress(to));
			mailMessage.setSubject("Newsmth: " + name);
			mailMessage.setContent(multiPart);
			Transport.send(mailMessage);
		} catch (MessagingException ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}
}