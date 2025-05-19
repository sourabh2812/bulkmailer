package com.ssuman.bulkmail;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class SendMailFrame extends JFrame {
    private JTextField txtEmailFile, txtContentFile, txtAttachment, txtCC, txtSubject;
    private JLabel lblProgress;
    private JButton btnSend, btnPause, btnResume;
    private int totalEmails = 0;

    public SendMailFrame() {
        initUI();
    }

    private void initUI() {
        // Load icon image from resources
        Image icon = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/app_icon.png"));
        setIconImage(icon);

        btnPause = new JButton("Pause");
        btnResume = new JButton("Resume");
        btnSend = new JButton("Send");

        btnPause.setEnabled(false);
        btnResume.setEnabled(false);

        setTitle("Bulk Mail Sender");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JLabel lblTo = new JLabel("To File:");
        JLabel lblCC = new JLabel("CC:");
        JLabel lblSubject = new JLabel("Subject:");
        JLabel lblContent = new JLabel("Content File:");
        JLabel lblAttachment = new JLabel("Attachment:");
        JLabel lblAuthor = new JLabel("Developed By: Sourabh Suman", SwingConstants.CENTER);

        txtEmailFile = new JTextField(20);
        txtCC = new JTextField(20);
        txtSubject = new JTextField(20);
        txtContentFile = new JTextField(20);
        txtAttachment = new JTextField(20);

        JButton btnBrowseTo = new JButton("Browse");
        JButton btnBrowseContent = new JButton("Browse");
        JButton btnBrowseAttachment = new JButton("Browse");

        lblProgress = new JLabel("Sent: 0 / 0", SwingConstants.CENTER);

        btnBrowseTo.addActionListener(e -> {
            chooseFile(txtEmailFile);
            updateTotalCount(txtEmailFile.getText().trim());
        });

        btnBrowseContent.addActionListener(e -> chooseFile(txtContentFile));
        btnBrowseAttachment.addActionListener(e -> chooseFile(txtAttachment));

        btnSend.addActionListener(e -> startSending());

        styleButton(btnSend, new Color(0, 123, 255), Color.WHITE);      // Blue
        styleButton(btnPause, new Color(255, 193, 7), Color.BLACK);     // Yellow
        styleButton(btnResume, new Color(40, 167, 69), Color.WHITE);    // Green

        styleButton(btnBrowseTo, Color.LIGHT_GRAY, Color.BLACK);
        styleButton(btnBrowseContent, Color.LIGHT_GRAY, Color.BLACK);
        styleButton(btnBrowseAttachment, Color.LIGHT_GRAY, Color.BLACK);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Increased spacing: 10 pixels all sides
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; panel.add(lblTo, gbc);
        gbc.gridx = 1; panel.add(txtEmailFile, gbc);
        gbc.gridx = 2; panel.add(btnBrowseTo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(lblCC, gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; panel.add(txtCC, gbc); gbc.gridwidth = 1;

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(lblSubject, gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; panel.add(txtSubject, gbc); gbc.gridwidth = 1;

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(lblContent, gbc);
        gbc.gridx = 1; panel.add(txtContentFile, gbc);
        gbc.gridx = 2; panel.add(btnBrowseContent, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(lblAttachment, gbc);
        gbc.gridx = 1; panel.add(txtAttachment, gbc);
        gbc.gridx = 2; panel.add(btnBrowseAttachment, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; panel.add(btnSend, gbc);
        gbc.gridx = 1; panel.add(btnPause, gbc);
        gbc.gridx = 2; panel.add(btnResume, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        panel.add(lblProgress, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(lblAuthor, gbc);

        add(panel);

        setSize(600, 400);
        setLocationRelativeTo(null);
    }

    private void chooseFile(JTextField targetField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            targetField.setText(file.getAbsolutePath());
        }
    }

    private void updateTotalCount(String filePath) {
        try {
            List<String> lines = Files.readAllLines(new File(filePath).toPath());
            totalEmails = (int) lines.stream()
                    .flatMap(line -> Arrays.stream(line.split(",")))
                    .map(String::trim)
                    .filter(email -> !email.isEmpty())
                    .count();
            lblProgress.setText("Sent: 0 / " + totalEmails + " (Remaining: " + totalEmails + ")");
        } catch (IOException e) {
            totalEmails = 0;
            lblProgress.setText("Sent: 0 / 0");
        }
    }

    private void startSending() {
        btnPause.setEnabled(true);
        btnResume.setEnabled(false);
        btnSend.setEnabled(false);

        setInputsEnabled(false);

        // Remove previous listeners to avoid multiple triggers
        for (var al : btnPause.getActionListeners()) {
            btnPause.removeActionListener(al);
        }
        for (var al : btnResume.getActionListeners()) {
            btnResume.removeActionListener(al);
        }

        btnPause.addActionListener(e -> {
            SendMail.pause();
            btnPause.setEnabled(false);
            btnResume.setEnabled(true);
        });

        btnResume.addActionListener(e -> {
            SendMail.resume();
            btnPause.setEnabled(true);
            btnResume.setEnabled(false);
        });

        String emailFile = txtEmailFile.getText().trim();
        String contentFile = txtContentFile.getText().trim();
        String cc = txtCC.getText().trim();
        String subject = txtSubject.getText().trim();
        String attachment = txtAttachment.getText().trim();

        if (emailFile.isEmpty() || contentFile.isEmpty() || subject.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in To file, Content file, and Subject.");
            setInputsEnabled(true);
            btnSend.setEnabled(true);
            btnPause.setEnabled(false);
            btnResume.setEnabled(false);
            return;
        }

        new Thread(() -> {
            String result = SendMail.sendBulkEmails(emailFile, contentFile, cc, subject, attachment,
                    new SendMail.ProgressListener() {
                        @Override
                        public void onStart(int total) {
                            SwingUtilities.invokeLater(() ->
                                    lblProgress.setText("Sent: 0 / " + total + " (Remaining: " + total + ")"));
                        }

                        @Override
                        public void onProgress(int sent, int total) {
                            SwingUtilities.invokeLater(() ->
                                    lblProgress.setText("Sent: " + sent + " / " + total + " (Remaining: " + (total - sent) + ")"));
                        }
                    });

            SwingUtilities.invokeLater(() -> {
                btnPause.setEnabled(false);
                btnResume.setEnabled(false);
                btnSend.setEnabled(true);
                setInputsEnabled(true);

                JOptionPane.showMessageDialog(this, result.equals("success") ?
                        "Emails sent successfully!" : "Some errors occurred. Check error.log.");
            });
        }).start();
    }

    private void setInputsEnabled(boolean enabled) {
        txtEmailFile.setEnabled(enabled);
        txtContentFile.setEnabled(enabled);
        txtAttachment.setEnabled(enabled);
        txtCC.setEnabled(enabled);
        txtSubject.setEnabled(enabled);
    }

    private void styleButton(JButton button, Color bg, Color fg) {
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> new SendMailFrame().setVisible(true));
    }
}
