# Explorer

Explore the world wide web customized to your wish.

## Online Demo

[https://x.chuntung.com](https://x.chuntung.com)

### Use as a docker mirror

vi `/etc/docker/daemon.json`

```yaml
{
   "registry-mirrors": ["https://docker.x.chuntung.com"]
 }
```

Login mirror using Docker Hub [Personal access tokens](https://docs.docker.com/security/access-tokens/)

`docker login docker.x.chuntung.com -u <your-account>`

## Local Debug

Start from source code `gradlew bootRun`

or run by docker image `docker run --name explorer -p 2024:2024 -p 2025:2025 chuntungho/explorer`

Access the URL: [http://localhost:2024](http://localhost:2024)

## Production Deployment

> This requires wildcard domain certificate and wildcard DNS record.
> 
> Replace `<your-domain.com>` with your own domain in nginx config.

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
    image: chuntungho/explorer
    command: --spring.profiles.active=prod
    ports:
      - 2024:2024
      - 2025:2025
    volumes:
      - application-prod.yml:/app/application-prod.yml
```
application-prod.yml

```yaml
# production config
explorer:
  # change this if the host differs from wildcard host
  explorer-url: <your-domain.com>
  # change this if it differs from the host in explorer url
  wildcard-host: <your-domain.com>
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
        server_name <your-domain.com>;

        ssl_certificate /etc/certs/your-domain/fullchain.cer;
        ssl_certificate_key /etc/certs/your-domain/private.key;

        location / {
                proxy_http_version 1.1;
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
        server_name *.<your-domain.com>;

        ssl_certificate /etc/certs/your-domain/fullchain.cer;
        ssl_certificate_key /etc/certs/your-domain/private.key;

        location / {
                proxy_http_version 1.1;
                proxy_set_header   Connection       "";
                proxy_set_header Host $http_host;
                proxy_set_header X-Real-IP $remote_addr;
                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                proxy_set_header X-Forwarded-Proto $scheme;
                proxy_pass http://explorer:2025;
        }
}
```