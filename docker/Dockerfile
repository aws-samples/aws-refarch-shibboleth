ARG SHIB_VERSION=latest

FROM tier/shib-idp:${SHIB_VERSION}

ARG SHIB_VERSION=latest
ARG AWS_DEFAULT_REGION 
ARG AWS_ACCESS_KEY_ID 
ARG AWS_SECRET_ACCESS_KEY 
ARG AWS_SESSION_TOKEN
ARG PARENT_DOMAIN
ARG FULLY_QUALIFIED_DOMAIN_NAME
ARG SECRETS_MANAGER_SIGNING_ARN
ARG SECRETS_MANAGER_BACKCHANNEL_ARN
ARG SECRETS_MANAGER_ENCRYPTION_ARN
ARG SECRETS_MANAGER_SEALER_KEY_ARN
ARG SECRETS_MANAGER_LDAP_SETTINGS_ARN
#ARG JAR_JSR_VERSION=3.0.2
#ARG JAR_SHIB_SUPPORT_VERSION=9.0.0
#ARG JAR_SHIB_SECURITY_VERSION=9.0.0
#ARG JAR_LOGBACK_VERSION=1.4.14

ADD config/shib-idp/conf/idp.properties config/shib-idp/conf/ldap.properties.* config/shib-idp/conf/metadata-providers.xml.* config/shib-idp/conf/attribute-resolver.xml config/shib-idp/conf/attribute-filter.xml config/shib-idp/conf/saml-nameid.xml config/shib-idp/conf/global.xml.* /opt/shibboleth-idp/conf/
ADD config/shib-idp/metadata/idp-metadata.xml /opt/shibboleth-idp/metadata/
ADD config/shib-idp/metadata/sp /opt/shibboleth-idp/metadata/sp
ADD config/shib-idp/credentials/ldap-server.truststore /opt/shibboleth-idp/credentials/
ADD config/shib-idp/messages /opt/shibboleth-idp/messages
ADD config/shib-idp/edit-webapp /opt/shibboleth-idp/edit-webapp

# Build and add the AWSSecretsManagerKeyStrategy.class file to the IdP webapp
RUN mkdir -p /tmp/aws-secrets-manager-build
ADD src/AWSSecretsManagerKeyStrategy-Shibboleth4.java /tmp/aws-secrets-manager-build/AWSSecretsManagerKeyStrategy-Shibboleth4.java
ADD src/AWSSecretsManagerKeyStrategy-Shibboleth5.java /tmp/aws-secrets-manager-build/AWSSecretsManagerKeyStrategy-Shibboleth5.java
RUN echo "Building the custom DataSealerKeyStrategy implementation that uses AWS Secrets Manager"; \
    version=`echo ${SHIB_VERSION} | sed -r 's/^[^0-9]*([0-9]+).*/\1/g'` && \
    if [ "$version" = "latest" ]; then \
      version="5"; \
    fi && \
    mv "/opt/shibboleth-idp/conf/ldap.properties.v$version" /opt/shibboleth-idp/conf/ldap.properties && \
    rm -f /opt/shibboleth-idp/conf/ldap.properties.v* && \
    mv "/opt/shibboleth-idp/conf/metadata-providers.xml.v$version" /opt/shibboleth-idp/conf/metadata-providers.xml && \
    rm -f /opt/shibboleth-idp/conf/metadata-providers.xml.v* && \
    mv "/opt/shibboleth-idp/conf/global.xml.v$version" /opt/shibboleth-idp/conf/global.xml && \
    rm -f /opt/shibboleth-idp/conf/global.xml.v* && \
    if [ "$version" = "4" ]; then \
      mkdir -p /tmp/aws-java-sdk-installation && \
      cd /tmp/aws-java-sdk-installation && \
      wget -q https://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip && \
      unzip -q -o aws-java-sdk.zip && \
      rm aws-java-sdk-*/lib/aws-java-sdk-*-javadoc.jar && \
      rm aws-java-sdk-*/lib/aws-java-sdk-*-sources.jar && \
      cp aws-java-sdk-*/lib/aws-java-sdk-*.jar /opt/shibboleth-idp/edit-webapp/WEB-INF/lib/ && \
      cd /tmp/aws-secrets-manager-build && \
      rm -rf /tmp/aws-java-sdk-installation && \
      mv "AWSSecretsManagerKeyStrategy-Shibboleth$version.java" AWSSecretsManagerKeyStrategy.java && \
      rm -f AWSSecretsManagerKeyStrategy-Shibboleth*.java && \
      javac -cp /opt/shibboleth-idp/dist/webapp/WEB-INF/lib/*:/opt/shibboleth-idp/edit-webapp/WEB-INF/lib/* AWSSecretsManagerKeyStrategy.java -d /opt/shibboleth-idp/edit-webapp/WEB-INF/classes/; \
    else \
      yum -y install maven xmlstarlet && \
      cd /tmp/aws-secrets-manager-build && \
      mvn archetype:generate -DarchetypeGroupId=software.amazon.awssdk -DarchetypeArtifactId=archetype-app-quickstart -Dservice=secretsmanager -DhttpClient=apache-client -DnativeImage=false -D credentialProvider=default -DgroupId=AWSSecretsManagerKeyStrategy -DartifactId=AWSSecretsManagerKeyStrategy -Dversion=1.0.0 -Dpackage=AWSSecretsManagerKeyStrategy -DinteractiveMode=false && \
      mv "AWSSecretsManagerKeyStrategy-Shibboleth$version.java" AWSSecretsManagerKeyStrategy/src/main/java/AWSSecretsManagerKeyStrategy/AWSSecretsManagerKeyStrategy.java && \
      rm -f AWSSecretsManagerKeyStrategy-Shibboleth*.java && \
      cd AWSSecretsManagerKeyStrategy && \
      jar_jsr_version=`ls /opt/shibboleth-idp/dist/webapp/WEB-INF/lib/jsr305-[0-9*].*.jar | sed -r 's/.*-(.*).jar/\1/g'` && \
      jar_shib_support_version=`ls /opt/shibboleth-idp/dist/webapp/WEB-INF/lib/shib-support-[0-9*].*.jar | sed -r 's/.*-(.*).jar/\1/g'` && \
      jar_shib_security_version=`ls /opt/shibboleth-idp/dist/webapp/WEB-INF/lib/shib-security-[0-9*].*.jar | sed -r 's/.*-(.*).jar/\1/g'` && \
      jar_logback_version=`ls /opt/shibboleth-idp/dist/webapp/WEB-INF/lib/logback-classic-[0-9*].*.jar | sed -r 's/.*-(.*).jar/\1/g'` && \
      mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file -Dfile=/opt/shibboleth-idp/dist/webapp/WEB-INF/lib/jsr305-${jar_jsr_version}.jar && \
      mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file -Dfile=/opt/shibboleth-idp/dist/webapp/WEB-INF/lib/shib-support-${jar_shib_support_version}.jar && \
      mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file -Dfile=/opt/shibboleth-idp/dist/webapp/WEB-INF/lib/shib-security-${jar_shib_security_version}.jar && \
      xmlstarlet ed --inplace -N pom=http://maven.apache.org/POM/4.0.0 -s /pom:project/pom:dependencies -t elem -n dependency -v "" --var dependency '$prev' -s '$dependency' -t elem -n groupId -v com.google.code.findbugs -s '$dependency' -t elem -n artifactId -v jsr305 -s '$dependency' -t elem -n version -v ${jar_jsr_version} pom.xml && \
      xmlstarlet ed --inplace -N pom=http://maven.apache.org/POM/4.0.0 -s /pom:project/pom:dependencies -t elem -n dependency -v "" --var dependency '$prev' -s '$dependency' -t elem -n groupId -v net.shibboleth -s '$dependency' -t elem -n artifactId -v shib-support -s '$dependency' -t elem -n version -v ${jar_shib_support_version} pom.xml && \
      xmlstarlet ed --inplace -N pom=http://maven.apache.org/POM/4.0.0 -s /pom:project/pom:dependencies -t elem -n dependency -v "" --var dependency '$prev' -s '$dependency' -t elem -n groupId -v net.shibboleth -s '$dependency' -t elem -n artifactId -v shib-security -s '$dependency' -t elem -n version -v ${jar_shib_security_version} pom.xml && \
      xmlstarlet ed --inplace -N pom="http://maven.apache.org/POM/4.0.0" -d "/pom:project/pom:dependencies/pom:dependency[pom:groupId='org.slf4j']" pom.xml && \
      xmlstarlet ed --inplace -N pom=http://maven.apache.org/POM/4.0.0 -s /pom:project/pom:dependencies -t elem -n dependency -v "" --var dependency '$prev' -s '$dependency' -t elem -n groupId -v ch.qos.logback -s '$dependency' -t elem -n artifactId -v logback-classic -s '$dependency' -t elem -n version -v ${jar_logback_version} pom.xml && \
      xmlstarlet ed --inplace -N pom=http://maven.apache.org/POM/4.0.0 -s /pom:project/pom:build/pom:plugins -t elem -n plugin -v "" --var plugin '$prev' -s '$plugin' -t elem -n artifactId -v maven-assembly-plugin -s '$plugin' -t elem -n executions -v "" --var executions '$prev' -s '$executions' -t elem -n execution -v "" --var execution '$prev' -s '$execution' -t elem -n phase -v package -s '$execution' -t elem -n goals -v "" --var goals '$prev' -s '$goals' -t elem -n goal -v single -s '$plugin' -t elem -n configuration -v "" -s '$prev' -t elem -n descriptorRefs -v "" -s '$prev' -t elem -n descriptorRef -v jar-with-dependencies pom.xml && \
      mvn package && \
      cp target/AWSSecretsManagerKeyStrategy-1.0.0-jar-with-dependencies.jar /opt/shibboleth-idp/edit-webapp/WEB-INF/lib/; \
    fi && \
    cd ~ && \
    rm -rf /tmp/aws-secrets-manager-build

# Install AWS CLI
RUN echo "Installing the AWS CLI"; \
    mkdir -p /tmp/aws-cli-installation && \
    cd /tmp/aws-cli-installation && \
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
    unzip awscliv2.zip && \
    ./aws/install -i /usr/local/aws -b /usr/local/bin && \
    cd ~ && \
    rm -rf /tmp/aws-cli-installation

# Install jq
RUN yum -y install jq

# Install openssl
RUN yum -y install openssl

# Grab the signing key and cert from secrets manager and save them as needed (If the secret value is empty, seed it).
RUN signing=`aws --region ${AWS_DEFAULT_REGION} secretsmanager get-secret-value --secret-id ${SECRETS_MANAGER_SIGNING_ARN} --query 'SecretString' | sed -e 's/^"//' -e 's/"$//'` && \
    if [ -z "$signing" ]; then \
      openssl req -new -x509 -nodes -newkey rsa:2048 -keyout ./signing-key.pem -days 3650 -subj "/CN=${FULLY_QUALIFIED_DOMAIN_NAME}" -out ./signing-cert.pem && \
      key=`awk 'NF {sub(/\r/, ""); printf "%s\\\\n",$0;}' ./signing-key.pem` && \
      cert=`awk 'NF {sub(/\r/, ""); printf "%s\\\\n",$0;}' ./signing-cert.pem` && \
      aws --region ${AWS_DEFAULT_REGION} secretsmanager put-secret-value --secret-id ${SECRETS_MANAGER_SIGNING_ARN} --secret-string "{\"key\":\"$key\",\"cert\":\"$cert\"}" && \
      rm ./signing-key.pem && \
      rm ./signing-cert.pem && \
      signing=`aws --region ${AWS_DEFAULT_REGION} secretsmanager get-secret-value --secret-id ${SECRETS_MANAGER_SIGNING_ARN} --query 'SecretString' | sed -e 's/^"//' -e 's/"$//'`; \
    fi && \
    echo $signing | sed -e 's/\\"/"/g' | jq '.key' | sed -e 's/^"//' -e 's/"$//' -e 's/\\\\n/\n/g' > /opt/shibboleth-idp/credentials/idp-signing.key && \
    echo $signing | sed -e 's/\\"/"/g' | jq '.cert' | sed -e 's/^"//' -e 's/"$//' -e 's/\\\\n/\n/g' > /opt/shibboleth-idp/credentials/idp-signing.crt && \
    cert=$(echo $signing | sed -e 's/\\"/"/g' | jq '.cert' | sed -e 's/^"//' -e 's/"$//' -e 's/\\\\n/\\n/g' -e 's/-*BEGIN CERTIFICATE-*\\n//' -e 's/\\n-*END CERTIFICATE-*\\n//') && \
    sed -i "s|XXXX-SigningPublicCert-XXXX|$cert|g" /opt/shibboleth-idp/metadata/idp-metadata.xml

# Grab the backchannel key and cert from secrets manager and save them as needed (If the secret value is empty, seed it).
RUN backchannel=`aws --region ${AWS_DEFAULT_REGION} secretsmanager get-secret-value --secret-id ${SECRETS_MANAGER_BACKCHANNEL_ARN} --query 'SecretString' | sed -e 's/^"//' -e 's/"$//'` && \
    if [ -z "$backchannel" ]; then \
      openssl req -new -x509 -nodes -newkey rsa:2048 -keyout ./backchannel-key.pem -days 3650 -subj "/CN=${FULLY_QUALIFIED_DOMAIN_NAME}" -out ./backchannel-cert.pem && \
      key=`awk 'NF {sub(/\r/, ""); printf "%s\\\\n",$0;}' ./backchannel-key.pem` && \
      cert=`awk 'NF {sub(/\r/, ""); printf "%s\\\\n",$0;}' ./backchannel-cert.pem` && \
      aws --region ${AWS_DEFAULT_REGION} secretsmanager put-secret-value --secret-id ${SECRETS_MANAGER_BACKCHANNEL_ARN} --secret-string "{\"key\":\"$key\",\"cert\":\"$cert\"}" && \
      rm ./backchannel-key.pem && \
      rm ./backchannel-cert.pem && \
      backchannel=`aws --region ${AWS_DEFAULT_REGION} secretsmanager get-secret-value --secret-id ${SECRETS_MANAGER_BACKCHANNEL_ARN} --query 'SecretString' | sed -e 's/^"//' -e 's/"$//'`; \
    fi && \
    echo $backchannel | sed -e 's/\\"/"/g' | jq '.key' | sed -e 's/^"//' -e 's/"$//' -e 's/\\\\n/\n/g' > /opt/shibboleth-idp/credentials/idp-backchannel.key && \
    echo $backchannel | sed -e 's/\\"/"/g' | jq '.cert' | sed -e 's/^"//' -e 's/"$//' -e 's/\\\\n/\n/g' > /opt/shibboleth-idp/credentials/idp-backchannel.crt && \
    cert=$(echo $backchannel | sed -e 's/\\"/"/g' | jq '.cert' | sed -e 's/^"//' -e 's/"$//' -e 's/\\\\n/\\n/g' -e 's/-*BEGIN CERTIFICATE-*\\n//' -e 's/\\n-*END CERTIFICATE-*\\n//') && \
    sed -i "s|XXXX-BackchannelPublicCert-XXXX|$cert|g" /opt/shibboleth-idp/metadata/idp-metadata.xml

# Grab the encryption key and cert from secrets manager and save them as needed (If the secret value is empty, seed it).
RUN encryption=`aws --region ${AWS_DEFAULT_REGION} secretsmanager get-secret-value --secret-id ${SECRETS_MANAGER_ENCRYPTION_ARN} --query 'SecretString' | sed -e 's/^"//' -e 's/"$//'` && \
    if [ -z "$encryption" ]; then \
      openssl req -new -x509 -nodes -newkey rsa:2048 -keyout ./encryption-key.pem -days 3650 -subj "/CN=${FULLY_QUALIFIED_DOMAIN_NAME}" -out ./encryption-cert.pem && \
      key=`awk 'NF {sub(/\r/, ""); printf "%s\\\\n",$0;}' ./encryption-key.pem` && \
      cert=`awk 'NF {sub(/\r/, ""); printf "%s\\\\n",$0;}' ./encryption-cert.pem` && \
      aws --region ${AWS_DEFAULT_REGION} secretsmanager put-secret-value --secret-id ${SECRETS_MANAGER_ENCRYPTION_ARN} --secret-string "{\"key\":\"$key\",\"cert\":\"$cert\"}" && \
      rm ./encryption-key.pem && \
      rm ./encryption-cert.pem && \
      encryption=`aws --region ${AWS_DEFAULT_REGION} secretsmanager get-secret-value --secret-id ${SECRETS_MANAGER_ENCRYPTION_ARN} --query 'SecretString' | sed -e 's/^"//' -e 's/"$//'`; \
    fi && \
    echo $encryption | sed -e 's/\\"/"/g' | jq '.key' | sed -e 's/^"//' -e 's/"$//' -e 's/\\\\n/\n/g' > /opt/shibboleth-idp/credentials/idp-encryption.key && \
    echo $encryption | sed -e 's/\\"/"/g' | jq '.cert' | sed -e 's/^"//' -e 's/"$//' -e 's/\\\\n/\n/g' > /opt/shibboleth-idp/credentials/idp-encryption.crt && \
    cert=$(echo $encryption | sed -e 's/\\"/"/g' | jq '.cert' | sed -e 's/^"//' -e 's/"$//' -e 's/\\\\n/\\n/g' -e 's/-*BEGIN CERTIFICATE-*\\n//' -e 's/\\n-*END CERTIFICATE-*\\n//') && \
    sed -i "s|XXXX-EncryptionPublicCert-XXXX|$cert|g" /opt/shibboleth-idp/metadata/idp-metadata.xml

# Replace domain names in IdP config files with proper domains
RUN sed -i "s|XXXX-ParentDomain-XXXX|$PARENT_DOMAIN|g" /opt/shibboleth-idp/conf/idp.properties && \
    sed -i "s|XXXX-FullyQualifiedDomainName-XXXX|$FULLY_QUALIFIED_DOMAIN_NAME|g" /opt/shibboleth-idp/conf/idp.properties && \
    sed -i "s|XXXX-ParentDomain-XXXX|$PARENT_DOMAIN|g" /opt/shibboleth-idp/metadata/idp-metadata.xml && \
    sed -i "s|XXXX-ParentDomain-XXXX|$PARENT_DOMAIN|g" /opt/shibboleth-idp/conf/attribute-resolver.xml && \
    sed -i "s|XXXX-FullyQualifiedDomainName-XXXX|$FULLY_QUALIFIED_DOMAIN_NAME|g" /opt/shibboleth-idp/metadata/idp-metadata.xml

# Make sure that Tomcat knows the Sealer Key Secret ID
RUN echo "export SEALER_KEY_SECRET_ID=${SECRETS_MANAGER_SEALER_KEY_ARN}" >> /usr/local/tomcat/bin/setenv.sh

# Grab the LDAP settings from secrets manager and save them as needed.
RUN ldap_settings=`aws --region ${AWS_DEFAULT_REGION} secretsmanager get-secret-value --secret-id ${SECRETS_MANAGER_LDAP_SETTINGS_ARN} --query 'SecretString' | sed -e 's/^"//' -e 's/"$//'` && \
    ldap_url=$(echo $ldap_settings | sed -e 's/\\"/"/g' | jq '.ldap_url' | sed -e 's/^"//' -e 's/"$//') && \
    base_dn=$(echo $ldap_settings | sed -e 's/\\"/"/g' | jq '.base_dn' | sed -e 's/^"//' -e 's/"$//') && \
    read_only_user=$(echo $ldap_settings | sed -e 's/\\"/"/g' | jq '.read_only_user' | sed -e 's/^"//' -e 's/"$//') && \
    read_only_password=$(echo $ldap_settings | sed -e 's/\\"/"/g' | jq '.read_only_password' | sed -e 's/^"//' -e 's/"$//' -e 's/|/\\|/g' -e 's/\$/\\$/g') && \
    sed -i "s|XXXX-LDAPURL-XXXX|$ldap_url|g" /opt/shibboleth-idp/conf/ldap.properties && \
    sed -i "s|XXXX-BaseDN-XXXX|$base_dn|g" /opt/shibboleth-idp/conf/ldap.properties && \
    sed -i "s|XXXX-ReadOnlyUser-XXXX|$read_only_user|g" /opt/shibboleth-idp/conf/ldap.properties && \
    sed -i "s|XXXX-ReadOnlyPassword-XXXX|$read_only_password|g" /opt/shibboleth-idp/conf/ldap.properties
