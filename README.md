### Docker image build
```
$> docker build .
```

### Create temporary container
```
$> docker run -t -i --privileged <IMAGE_ID> sh
```

### Run fetch commands inside the container
```
$> cd /code 
$> ./fetch https://www.google.com https://github.com
```

### List the saved website folders
```
$> cd /tmp
$> ls
github.com.html www.google.com.html
```

### Metadata list
```
$> ./fetch --metadata https://www.google.com
site: www.google.com
num_links: 35
images: 3
last_fetch: Tue Mar 16 2021 15:46 UTC
```

### Zip the website local content
```
$> ./fetch --zip https://www.google.com
```

### List the zipped website local content
```
$> cd /tmp
$> ls |grep ".zip"
www.google.com.html.zip
```
