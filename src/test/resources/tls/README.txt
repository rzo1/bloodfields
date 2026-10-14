TEST FIXTURE ONLY. Self-signed localhost certificate generated solely for automated TLS tests. The private key is public by design (it lives in git) and must never be used to serve anything real. Generate your own with:
  openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 825 -nodes -subj "/CN=your.host" -addext "subjectAltName=DNS:your.host"
