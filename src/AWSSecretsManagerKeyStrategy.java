import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.SecretKey;

import net.shibboleth.utilities.java.support.annotation.constraint.NonnullAfterInit;
import net.shibboleth.utilities.java.support.annotation.constraint.NotEmpty;
import net.shibboleth.utilities.java.support.collection.Pair;
import net.shibboleth.utilities.java.support.component.AbstractInitializableComponent;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;
import net.shibboleth.utilities.java.support.logic.ConstraintViolationException;
import net.shibboleth.utilities.java.support.primitive.StringSupport;
import net.shibboleth.utilities.java.support.primitive.TimerSupport;
import net.shibboleth.utilities.java.support.resource.Resource;
import net.shibboleth.utilities.java.support.security.DataSealerKeyStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.secretsmanager.*;
import com.amazonaws.services.secretsmanager.model.*;

import java.util.Base64;

public class AWSSecretsManagerKeyStrategy extends AbstractInitializableComponent implements DataSealerKeyStrategy {

  @Nonnull private Logger log = LoggerFactory.getLogger(AWSSecretsManagerKeyStrategy.class);

  private String secretId;

  /** {@inheritDoc} */
  @Override
  public void doInitialize() throws ComponentInitializationException {
    log.info("doInitialize()");
    try {
      secretId = StringSupport.trimOrNull(System.getenv("SEALER_KEY_SECRET_ID"));
      Constraint.isNotNull(secretId, "Environment variable SEALER_KEY_SECRET_ID cannot be null");
    } catch (final ConstraintViolationException e) {
      throw new ComponentInitializationException(e);
    }
    getDefaultKey();
  }

  public String createKey() {
    log.info("createKey()");
    try {
      KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
      keyGenerator.init(128);
      SecretKey secretKey = keyGenerator.generateKey();
      byte[] secretKeyBytes = secretKey.getEncoded();
      AWSSecretsManager client = getClient();
      PutSecretValueRequest putSecretValueRequest = new PutSecretValueRequest();
      putSecretValueRequest.setSecretId(secretId);
      putSecretValueRequest.setSecretBinary(ByteBuffer.wrap(secretKeyBytes));
      return client.putSecretValue(putSecretValueRequest).getVersionId();
    } catch (NoSuchAlgorithmException ex) {
      log.error("COULD NOT CREATE KEY", ex);
    }
    return null;
  }

  public Pair<String, SecretKey> getDefaultKey() {
    log.info("getDefaultKey()");
    GetSecretValueResult getSecretValueResult = getSecretValueResult(null);
    SecretKey secretKey = getSecretKeyFromGetSecretValueResult(getSecretValueResult);
    if (secretKey != null) {
      log.info("getDefaultKey() returning new Pair => {}, {}", getSecretValueResult.getVersionId(), secretKey);
    }
    return new Pair<>(getSecretValueResult.getVersionId(), secretKey);
  }

  public SecretKey getKey(String versionId) throws KeyException {
    log.info("getKey() with secret id '{}' and version '{}'", secretId, versionId);

    if(versionId.length() < 32 || versionId.length() > 64) {
      throw new KeyException("Invalid version id length");
    }

    GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest().withSecretId(secretId)
        .withVersionId(versionId);
    GetSecretValueResult getSecretValueResult = getSecretValueResult(versionId);

    SecretKey secretKey = getSecretKeyFromGetSecretValueResult(getSecretValueResult);
    if (secretKey != null) {
      log.info("getKey({}) returning SecretKey => Format: {}, Algorithm: {}, Encoded Value: {}", versionId, secretKey.getFormat(), secretKey.getAlgorithm(), new String(secretKey.getEncoded()));
    } else {
      throw new KeyException("Key not found");
    } 
    return secretKey;
  }

  private AWSSecretsManager getClient() {
    return AWSSecretsManagerClientBuilder.defaultClient();
  }

  private GetSecretValueResult getSecretValueResult(String versionId) {
    log.info("getSecretValueResult({})", versionId);
    AWSSecretsManager client = getClient();

    GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest().withSecretId(secretId);
    if (versionId != null) {
      getSecretValueRequest = getSecretValueRequest.withVersionId(versionId);
    }
    try {
      return client.getSecretValue(getSecretValueRequest);
    } catch (ResourceNotFoundException e) {
      log.error("The requested secret {} was not found", secretId);
    } catch (InvalidRequestException e) {
      log.error("The request was invalid due to: {}", e.getMessage());
    } catch (InvalidParameterException e) {
      log.error("The request had invalid params: {}", e.getMessage());
    }
    return null;
  }

  private SecretKey getSecretKeyFromGetSecretValueResult(GetSecretValueResult getSecretValueResult) {
    SecretKey secretKey = null;
    //In addition to null values, exclude old versions that have been deprecated (all stage labels removed).
    if (getSecretValueResult != null && getSecretValueResult.getVersionStages() != null && getSecretValueResult.getVersionStages().size() > 0) {
      ByteBuffer binarySecretData = getSecretValueResult.getSecretBinary();
      byte[] arr = new byte[binarySecretData.remaining()];
      binarySecretData.get(arr);
      secretKey = new SecretKeySpec(arr, 0, arr.length, "AES");
    }
    return secretKey;
  }
}
