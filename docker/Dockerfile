FROM tier/shib-idp:latest

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

ADD config/shib-idp/conf/idp.properties config/shib-idp/conf/ldap.properties config/shib-idp/conf/metadata-providers.xml config/shib-idp/conf/attribute-resolver.xml config/shib-idp/conf/attribute-filter.xml config/shib-idp/conf/saml-nameid.xml config/shib-idp/conf/global.xml /opt/shibboleth-idp/conf/
ADD config/shib-idp/metadata/idp-metadata.xml /opt/shibboleth-idp/metadata/
ADD config/shib-idp/metadata/sp /opt/shibboleth-idp/metadata/sp
ADD config/shib-idp/credentials/ldap-server.truststore /opt/shibboleth-idp/credentials/
ADD config/shib-idp/messages /opt/shibboleth-idp/messages
ADD config/shib-idp/edit-webapp /opt/shibboleth-idp/edit-webapp

# Add the AWS Java SDK to the IdP webapp
RUN echo "Adding the AWS SDK for Java"; \
    mkdir -p /tmp/aws-java-sdk-installation && \
    cd /tmp/aws-java-sdk-installation && \
    wget -q https://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip && \
    unzip -q -o aws-java-sdk.zip && \
    rm aws-java-sdk-*/lib/aws-java-sdk-*-javadoc.jar && \
    rm aws-java-sdk-*/lib/aws-java-sdk-*-sources.jar && \
    cp aws-java-sdk-*/lib/aws-java-sdk-*.jar /opt/shibboleth-idp/edit-webapp/WEB-INF/lib/ && \
    cd ~ && \
    rm -rf /tmp/aws-java-sdk-installation

# Build and add the AWSSecretsManagerKeyStrategy.class file to the IdP webapp
RUN mkdir -p /tmp/aws-secrets-manager-build
ADD src/AWSSecretsManagerKeyStrategy.java /tmp/aws-secrets-manager-build/
RUN echo "Building the custom DataSealerKeyStrategy implementation that uses AWS Secrets Manager"; \
    cd /tmp/aws-secrets-manager-build && \
    javac -cp /opt/shibboleth-idp/dist/webapp/WEB-INF/lib/*:/opt/shibboleth-idp/edit-webapp/WEB-INF/lib/* \
      AWSSecretsManagerKeyStrategy.java -d /opt/shibboleth-idp/edit-webapp/WEB-INF/classes/ && \
    cd ~ && \
    rm -rf /tmp/aws-secrets-manager-build

# Install AWS CLI
RUN curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip" && \
    unzip awscli-bundle.zip && \
    ./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws && \
    rm -rf ./awscli-bundle ./awscli-bundle.zip

# Install jq
ENV JQ_VERSION='1.5'

RUN wget --no-check-certificate https://raw.githubusercontent.com/stedolan/jq/master/sig/jq-release.key -O /tmp/jq-release.key && \
    wget --no-check-certificate https://raw.githubusercontent.com/stedolan/jq/master/sig/v${JQ_VERSION}/jq-linux64.asc -O /tmp/jq-linux64.asc && \
    wget --no-check-certificate https://github.com/stedolan/jq/releases/download/jq-${JQ_VERSION}/jq-linux64 -O /tmp/jq-linux64 && \
    gpg --import /tmp/jq-release.key && \
    gpg --verify /tmp/jq-linux64.asc /tmp/jq-linux64 && \
    cp /tmp/jq-linux64 /usr/bin/jq && \
    chmod +x /usr/bin/jq && \
    rm -f /tmp/jq-release.key && \
    rm -f /tmp/jq-linux64.asc && \
    rm -f /tmp/jq-linux64

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
