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

or run by docker image `docker run --name explorer -p 8080:8080 chuntungho/explorer`

Access the URL: [http://localhost:8080](http://localhost:8080)

## Deployment

> This requires wildcard domain certificate and wildcard DNS record.
> 
> refer to [src/docker/docker-compose.yml]
