## Security

#### TLS:

The app expects a java keystore of certificates.
In order to create a new self-signed cert use:

`keytool -genkey -alias sitename -keyalg RSA -keystore keystore.jks -keysize 2048`

keystore path for the server is configurable via:

`-DkeyStore=secure/keystore.jks`


#### Authentication:

Basic HTTP auth for the server is cofigurable via:

`-DauthConfig=secure/credentials.txt`

Make sure the file is protected by safe access rights. The file syntax is:

`username=user`

`password=pass`


The keystore and the credentials for javaagent are configurable via last two parameters, see example below.


#### Running

To run as a secure javaagent:
```
java -javaagent:./jmx_prometheus_javaagent_secure-0.2.1.jar=5557:config.yaml:keystore.jks:credentials.txt -jar yourJar.jar
```

To run as an independent HTTPS server:
```
java -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.port=5555 -DkeyStore=keystore.jks -DauthConfig=credentials.txt -jar ./jmx_prometheus_httpserver_secure-0.2.1-jar-with-dependencies.jar 5557 config.yaml
```

Metrics will now be accessible at https://localhost:5557/metrics

See run_* sample scripts which runs rebuilt agents in `secure` project folder.
