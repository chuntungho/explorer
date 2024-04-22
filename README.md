# Explorer

Explore the web customized to your wish.

## Online Demo

[https://explorer.chuntung.com](https://pages.show)

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