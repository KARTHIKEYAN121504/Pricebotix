require("dotenv").config();
const express = require("express");
const mongoose = require("mongoose");
const cors = require("cors");
const bodyParser = require("body-parser");
const bcrypt = require("bcrypt");
const nodemailer = require("nodemailer");
const crypto = require("crypto");
const path = require("path");
const jwt = require("jsonwebtoken");

const app = express();
const PORT = 3500;

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(express.static(path.join(__dirname, "public")));

// MongoDB Connection
mongoose.connect(process.env.MONGO_URI, {
  useNewUrlParser: true,
  useUnifiedTopology: true,
});

// User Schema
const userSchema = new mongoose.Schema({
  username: String,
  email: { type: String, unique: true },
  phone: String,
  dob: String,
  password: String,
  googleId: String,
  profilePic: String,
  verified: { type: Boolean, default: false },
  verificationToken: String,
  resetToken: String,
});

const User = mongoose.model("User", userSchema);

// Nodemailer Setup
const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: process.env.EMAIL_USER,
    pass: process.env.EMAIL_PASS,
  },
});

// ✅ Register Route
app.post("/register", async (req, res) => {
  const { username, email, phone, dob, password } = req.body;

  try {
    const existingUser = await User.findOne({ email });
    if (existingUser)
      return res.status(400).json({ message: "Email already registered" });

    const hashedPassword = await bcrypt.hash(password, 10);
    const verificationToken = jwt.sign({ email }, process.env.JWT_SECRET, {
      expiresIn: "1d",
    });

    const user = new User({
      username,
      email,
      phone,
      dob,
      password: hashedPassword,
      verificationToken,
      verified: false,
    });

    await user.save();

    const verificationLink = `${process.env.BASE_URL}/verify-email/${verificationToken}`;

    await transporter.sendMail({
      from: `"Pricebotix" <${process.env.EMAIL_USER}>`,
      to: email,
      subject: "Verify your Pricebotix Email",
      html: `
        <h2>Welcome to Pricebotix!</h2>
        <p>Click below to verify your email address.</p>
        <a href="${verificationLink}" style="padding: 10px 20px; background: #4CAF50; color: white; text-decoration: none; border-radius: 4px;">Verify Email</a>
        <p>If you didn’t register, ignore this message.</p>
      `,
    });

    res.json({
      success: true,
      message: "Registered! Check your email to verify.",
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: "Server error" });
  }
});

// ✅ Email Verification Route
app.get("/verify-email/:token", async (req, res) => {
  try {
    const { token } = req.params;
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    const user = await User.findOne({ email: decoded.email });

    if (!user || user.verified)
      return res.status(400).send("Invalid or already verified.");

    user.verified = true;
    user.verificationToken = undefined;
    await user.save();

    res.redirect(`${process.env.FRONTEND_URL}/verified-success.html`);
  } catch (err) {
    console.error(err);
    res.status(400).send("Verification link is invalid or expired.");
  }
});

// ✅ Login
app.post("/login", async (req, res) => {
  try {
    const { username, password } = req.body;
    const user = await User.findOne({ username });
    if (!user) return res.status(400).send("User not found");

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) return res.status(401).send("Incorrect password");

    // Send only one response here
    return res.json({
      success: true,
      message: "Login successful",
      user: {
        username: user.username,
        email: user.email,
        profilePic: user.profilePic || null,
      },
    });
  } catch (error) {
    console.error("Login error:", error);
    return res.status(500).send("Server error");
  }
});

// ✅ Google Sign-In
app.post("/google-login", async (req, res) => {
  const { username, email, googleId, profilePic } = req.body;

  try {
    let user = await User.findOne({ email });

    if (!user) {
      user = new User({
        username,
        email,
        googleId,
        profilePic,
        password: "",
        verified: true,
      });
      await user.save();
    }

    // Send only one response
    res.json({
      success: true,
      message: "Login successful",
      user: {
        username: user.username,
        email: user.email,
        profilePic: user.profilePic || null,
      },
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, message: "Server error" });
  }
});

// Forgot Password - Step 1: Send email
app.post("/forgot-password", async (req, res) => {
  const { email } = req.body;
  const user = await User.findOne({ email });
  if (!user) return res.status(400).json({ message: "Email not registered" });

  const resetToken = crypto.randomBytes(32).toString("hex");
  user.resetToken = resetToken;
  await user.save();

  const resetLink = `${process.env.FRONTEND_URL}/reset-password.html?token=${resetToken}`;
  await transporter.sendMail({
    from: process.env.EMAIL_USER,
    to: email,
    subject: "Password Reset Request",
    html: `<p>Click here to reset your password: <a href="${resetLink}">Reset Password</a></p>`,
  });

  res.json({ message: "Password reset link sent to your email." });
});

// Reset Password - Step 2: Use token
app.post("/reset-password", async (req, res) => {
  const { token, newPassword } = req.body;
  const user = await User.findOne({ resetToken: token });
  if (!user)
    return res.status(400).json({ message: "Invalid or expired token" });

  user.password = await bcrypt.hash(newPassword, 10);
  user.resetToken = undefined;
  await user.save();

  res.json({ message: "Password reset successful" });
});

// ✅ Start Server
app.listen(PORT, () => {
  console.log(`Server running at http://localhost:${PORT}`);
});
