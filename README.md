# Explorer

Explore the web customized to your wish.

## Online Demo

[https://explorer.chuntung.com](https://explorer.chuntung.com)

## Local Debug

`gradle bootRun`

Access the URL: [http://localhost:2024](http://localhost:2024)

## Deployment in Docker

> This requires wildcard domain certificate and wildcard DNS record.

`gradle bootBuildImage`

docker-compose.yml

```yaml
version: '3.6'

services:
  nginx:
    image: nginx:alpine
    volumes:
      - certs:/etc/certs
      - nginx-explorer.conf:/etc/nginx/conf.d/explorer.conf
    ports:
      - 80:80
      - 443:443

  explorer:
    image: explorer
    command: --spring.profiles.active=prod
    volumes:
      - application-prod.yml:/workspace/application-prod.yml
```
nginx-explorer.conf

```conf
# set buffer size
client_max_body_size 64M;
large_client_header_buffers 4 32k;
fastcgi_buffers 16 32k;
fastcgi_buffer_size 32k;
proxy_buffer_size   128k;
proxy_buffers   4 256k;
proxy_busy_buffers_size   256k;

# explorer server
server {
        listen 443 ssl;
        server_name your-domain.com;

        ssl_certificate /etc/certs/your-domain/fullchain.cer;
        ssl_certificate_key /etc/certs/your-domain/private.key;

        location / {
                proxy_http_version 1.1;
                proxy_set_header x-internal-forward "y";
                proxy_set_header Connection       "";
                proxy_set_header Host $http_host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
                proxy_pass http://explorer:2024;
        }
}

# proxy server
server {
        listen 443 ssl;
        server_name *.your-domain.com;

        ssl_certificate /etc/certs/your-domain/fullchain.cer;
        ssl_certificate_key /etc/certs/your-domain/private.key;

        location / {
                proxy_http_version 1.1;
                proxy_set_header   Connection       "";
                proxy_set_header Host $http_host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
                proxy_pass http://explorer:2024;
        }
}
```