// emailService.js
const nodemailer = require("nodemailer");
const { v4: uuidv4 } = require("uuid");

const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: "pricebotix@gmail.", // replace with your Gmail
    pass: "karthi@15", // App password from Google
  },
});

const sendVerificationEmail = async (email, type) => {
  const token = uuidv4();
  const verifyLink = `http://localhost:3500/verify/${type}/${token}`;

  const subject =
    type === "register"
      ? "Verify your Pricebotix account"
      : "Reset your Pricebotix password";

  const html = `
    <h2>${subject}</h2>
    <p>Click the button below to ${
      type === "register" ? "verify your email" : "reset your password"
    }:</p>
    <a href="${verifyLink}" style="padding:10px 20px; background:#09baec; color:#fff; text-decoration:none; border-radius:5px;">
      ${type === "register" ? "Verify Email" : "Reset Password"}
    </a>
    <p>This link will expire in 10 minutes.</p>
  `;

  await transporter.sendMail({
    from: '"Pricebotix" <YOUR_EMAIL@gmail.com>',
    to: email,
    subject,
    html,
  });

  // Store token in your DB or a memory store (simplified here)
  return { token, verifyLink };
};

module.exports = { sendVerificationEmail };
