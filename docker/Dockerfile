FROM tier/shib-idp:latest

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
RUN echo "Building the custom DataSealerKeyStrategy implementation that uses AWS Secrets Manager"; \
    mkdir -p /tmp/aws-secrets-manager-build && \
    cd /tmp/aws-secrets-manager-build && \
    wget -q https://s3.amazonaws.com/aws-shibboleth-reference-architecture/src/AWSSecretsManagerKeyStrategy.java && \
    javac -cp /opt/shibboleth-idp/dist/webapp/WEB-INF/lib/*:/opt/shibboleth-idp/edit-webapp/WEB-INF/lib/* \
      AWSSecretsManagerKeyStrategy.java -d /opt/shibboleth-idp/edit-webapp/WEB-INF/classes/ && \
    cd ~ && \
    rm -rf /tmp/aws-secrets-manager-build

# Modify the shibboleth global-system.xml file so that it will use our custom AWS Secrets Manager based DataSealerKeyStrategy
RUN echo "Configuring Shibboleth to use the AWS Secrets Manager based DataSealerKeyStrategy"; \
    yum install -y xmlstarlet; \
    xmlstarlet ed -L -d "/_:beans/_:bean[@id='shibboleth.DataSealerKeyStrategy']" -s /_:beans -t elem -n bean -v "" -i /_:beans/bean -t attr -n id -v shibboleth.DataSealerKeyStrategy -i /_:beans/bean -t attr -n class -v AWSSecretsManagerKeyStrategy /opt/shibboleth-idp/system/conf/global-system.xml;
    #xmlstarlet ed -L -s /_:beans -t elem -n bean -v "" -i /_:beans/bean -t attr -n id -v AWSSecretsManagerDataSealerKeyStrategy -i /_:beans/bean -t attr -n class -v net.shibboleth.utilities.java.support.security.impl.ScriptedKeyStrategy -s /_:beans/bean -t elem -n property -v "" -i /_:beans/bean/property -t attr -n name -v keyScript -s /_:beans/bean/property -t elem -n bean -v "" -i /_:beans/bean/property/bean -t attr -n class -v net.shibboleth.utilities.java.support.scripting.EvaluableScript -s /_:beans/bean/property/bean -t elem -n constructor-arg -v "" -i /_:beans/bean/property/bean/constructor-arg -t attr -n name -v engineName -i /_:beans/bean/property/bean/constructor-arg -t attr -n value -v python -s /_:beans/bean/property/bean -t elem -n constructor-arg -v "" -i /_:beans/bean/property/bean/constructor-arg[2] -t attr -n name -v scriptSource -s /_:beans/bean/property/bean/constructor-arg[2] -t elem -n bean -v "" -i /_:beans/bean/property/bean/constructor-arg/bean -t attr -n class -v java.io.File -i /_:beans/bean/property/bean/constructor-arg/bean -t attr -n c:pathname -v %{idp.home}/conf/AWSSecretsManagerDataSealerKeyStrategy.py /opt/shibboleth-idp/system/conf/global-system.xml;
    #xmlstarlet ed -L -s /_:beans -t elem -n bean -v "" -i /_:beans/bean -t attr -n id -v AWSSecretsManagerDataSealerKeyStrategy -i /_:beans/bean -t attr -n class -v net.shibboleth.utilities.java.support.security.impl.ScriptedKeyStrategy -i /_:beans/bean -t attr -n p:updateInterval -v %{idp.sealer.updateInterval:PT15M} -s /_:beans/bean -t elem -n property -v "" -i /_:beans/bean/property -t attr -n name -v keyScript -s /_:beans/bean/property -t elem -n bean -v "" -i /_:beans/bean/property/bean -t attr -n class -v net.shibboleth.utilities.java.support.scripting.EvaluableScript -s /_:beans/bean/property/bean -t elem -n constructor-arg -v "" -i /_:beans/bean/property/bean/constructor-arg -t attr -n name -v engineName -i /_:beans/bean/property/bean/constructor-arg -t attr -n value -v javascript -s /_:beans/bean/property/bean -t elem -n constructor-arg -v "" -i /_:beans/bean/property/bean/constructor-arg[2] -t attr -n name -v scriptSource -s /_:beans/bean/property/bean/constructor-arg[2] -t elem -n bean -v "" -i /_:beans/bean/property/bean/constructor-arg/bean -t attr -n class -v java.io.File -i /_:beans/bean/property/bean/constructor-arg/bean -t attr -n c:pathname -v %{idp.home}/conf/AWSSecretsManagerDataSealerKeyStrategy.js /opt/shibboleth-idp/system/conf/global-system.xml;

# The build args below can be used at build-time to tell the build process where to find your config files.  This is for a completely burned-in config.
#ARG TOMCFG=config/tomcat
#ARG TOMCERT=credentials/tomcat
#ARG TOMWWWROOT=wwwroot
#ARG SHBCFG=config/shib-idp/conf
#ARG SHBCREDS=credentials/shib-idp
#ARG SHBVIEWS=config/shib-idp/views
#ARG SHBEDWAPP=config/shib-idp/edit-webapp
#ARG SHBMSGS=config/shib-idp/messages
#ARG SHBMD=config/shib-idp/metadata

# copy in the needed config files
#ADD ${TOMCFG} /usr/local/tomcat/conf
#ADD ${TOMCERT} /opt/certs
#ADD ${TOMWWWROOT} /usr/local/tomcat/webapps/ROOT
#ADD ${SHBCFG} /opt/shibboleth-idp/conf
#ADD ${SHBCREDS} /opt/shibboleth-idp/credentials
#ADD ${SHBVIEWS} /opt/shibboleth-idp/views
#ADD ${SHBEDWAPP} /opt/shibboleth-idp/edit-webapp
#ADD ${SHBMSGS} /opt/shibboleth-idp/messages
#ADD ${SHBMD} /opt/shibboleth-idp/metadata

ARG SECRETS_MANAGER_SECRET_ARN=secrets-manager-sealer-key
RUN echo "export SEALER_KEY_SECRET_ID=${SECRETS_MANAGER_SECRET_ARN}" >> /usr/local/tomcat/bin/setenv.sh
