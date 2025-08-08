
// Toggle chatbot visibility
function toggleChatbot() {
  const bot = document.getElementById("chatbot");
  bot.style.display = bot.style.display === "flex" ? "none" : "flex";
}

// Handle sending message
  const TOGETHER_API_KEY ="cc7b105633e27ab7b465e2ac16d03e382595a92c7f9a5455e54f3b5f8b4d92fe";
  const TOGETHER_MODEL = "meta-llama/Llama-3-8b-chat-hf";

  document.getElementById("sendBtn").addEventListener("click", sendMessage);
  document
    .getElementById("chatInput")
    .addEventListener("keypress", function (e) {
      if (e.key === "Enter") sendMessage();
    });

  async function sendMessage() {
    const input = document.getElementById("chatInput");
    const msg = input.value.trim();
    if (!msg) return;

    const chatBody = document.getElementById("chatBody");

    const userDiv = document.createElement("div");
    userDiv.className = "user-message";
    userDiv.innerText = msg;
    chatBody.appendChild(userDiv);

    const botDiv = document.createElement("div");
    botDiv.className = "bot-message";
    botDiv.innerText = "Typing...";
    chatBody.appendChild(botDiv);

    input.value = "";
    chatBody.scrollTop = chatBody.scrollHeight;

    try {
      const response = await fetch(
        "https://api.together.xyz/v1/chat/completions",
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${TOGETHER_API_KEY}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            model: TOGETHER_MODEL,
            messages: [
              { role: "system", content: "You are a helpful assistant." },
              { role: "user", content: msg },
            ],
            temperature: 0.7,
            max_tokens: 200,
          }),
        }
      );

      const data = await response.json();
      const reply =
        data.choices?.[0]?.message?.content || "⚠️ No reply received.";
      botDiv.innerText = reply;
    } catch (err) {
      console.error("API error:", err);
      botDiv.innerText = "⚠️ Error connecting to Together API.";
    }

    chatBody.scrollTop = chatBody.scrollHeight;
  }

// --- Product Search ---
async function performSearch(event) {
  if (event) event.preventDefault();

  const query = document.getElementById("searchInput").value.trim();
  const resultsDiv = document.getElementById("searchResults");

  resultsDiv.innerHTML = "<center><p>⏳ Searching...</p></center>";
  resultsDiv.style.display = "flex";

  if (!query) {
    resultsDiv.innerHTML =
      "<center><p>Please enter a product name.</p></center>";
    return;
  }

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 100000);

    const response = await fetch(
      `http://localhost:4567/search?q=${encodeURIComponent(query)}`,
      {
        signal: controller.signal,
      }
    );
    clearTimeout(timeout);

    if (!response.ok) throw new Error("Network response was not ok");

    const data = await response.json();
    console.log("📦 Combined Results:", data);

    if (!Array.isArray(data) || data.length === 0) {
      resultsDiv.innerHTML = "<center><p>No results found.</p></center>";
      return;
    }

    const usdToInr = 83;
    let html = "";

    data.forEach((item) => {
      const price =
        item.company === "eBay"
          ? `₹${(parseFloat(item.price || "0") * usdToInr).toFixed(2)} INR`
          : item.price || "N/A";

      const imageUrl = item.image?.startsWith("http")
        ? item.image
        : "https://dummyimage.com/200x200/cccccc/000000&text=No+Image";

      html += `
       <div class="product-card">
  <div class="product-content">
    <img src="${imageUrl}" alt="${item.name}" class="product-img">
    <h3>${item.name}</h3>
    <p class="price">${price}</p>
    <p class="site">From: ${item.company}</p>
  </div>
  <a href="${item.link}" target="_blank">View Product</a>
</div>

      `;
    });

    resultsDiv.innerHTML = html;
  } catch (error) {
    console.error("❌ Search error:", error);
    if (error.name === "AbortError") {
      resultsDiv.innerHTML =
        "<center><p>Error Search again its take time sorry for that!</p></center>";
    } else {
      resultsDiv.innerHTML =
        "<center><p>Something went wrong while searching.</p></center>";
    }
  }
  console.log("hi");
}

// --- Feedback Form Submission ---
document
  .getElementById("feedback-form")
  .addEventListener("submit", function (e) {
    e.preventDefault();
    const form = e.target;
    const data = new FormData(form);

    fetch("https://formspree.io/f/mgvzvnbk", {
      method: "POST",
      body: data,
      headers: {
        Accept: "application/json",
      },
    })
      .then((response) => {
        if (response.ok) {
          document.getElementById("form-success").style.display = "block";
          form.reset();
        } else {
          alert("There was an error sending your feedback.");
        }
      })
      .catch((error) => {
        alert("Network error while submitting form.");
      });
  });
