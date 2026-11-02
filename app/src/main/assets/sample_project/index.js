const http = require('http');

const PORT = process.env.PORT || 3000;

const server = http.createServer((req, res) => {
  res.statusCode = 200;
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({
    status: "ok",
    message: "Hello from Mobile Code IDE on Android!",
    timestamp: new Date().toISOString()
  }));
});

server.listen(PORT, () => {
  console.log(`Server running at http://localhost:${PORT}/`);
});

