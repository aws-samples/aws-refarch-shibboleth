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
import net.shibboleth.utilities.java.support.security.KeyNotFoundException;

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

  /** Current key version loaded. */
  @NonnullAfterInit private String currentVersion;

  /** Current default key loaded. */
  @NonnullAfterInit private SecretKey defaultKey;
    
  /** SecretsManager secret ID containing key.. */
  @NonnullAfterInit private String secretId;

  /** Time between key update checks. Default value: (PT15M). */
  @Nonnull private Duration updateInterval;

  /** Timer used to schedule update tasks. */
  private Timer updateTaskTimer;

  /** Timer used to schedule update tasks if no external one set. */
  private Timer internalTaskTimer;

  /** Task that checks for updated key version. */
  private TimerTask updateTask;
    
  /** Constructor. */
  /*
  public BasicKeystoreKeyStrategy() {
    updateInterval = Duration.ofMinutes(15);
  }
  */
    
  /**
   * Set the secret ID.
   * 
   * @param id the secret ID
   */
  public void setSecretId(@Nonnull @NotEmpty final String id) {
    ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        
    secretId = Constraint.isNotNull(id, "Secret ID cannot be null");
  }

  /**
   * Set the time between key update checks. A value of 0 indicates that no updates will be
   * performed.
   * 
   * This setting cannot be changed after the service has been initialized.
   * 
   * @param interval time between key update checks
   */
  public void setUpdateInterval(@Nonnull final Duration interval) {
    ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        
    Constraint.isNotNull(interval, "Interval cannot be null");
    Constraint.isFalse(interval.isNegative(), "Interval cannot be negative");

    updateInterval = interval;
  }

  /**
   * Set the timer used to schedule update tasks.
   * 
   * This setting cannot be changed after the service has been initialized.
   * 
   * @param timer timer used to schedule update tasks
   */
  public void setUpdateTaskTimer(@Nullable final Timer timer) {
    ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

    updateTaskTimer = timer;
  }
    
  /** {@inheritDoc} */
  @Override
  public void doInitialize() throws ComponentInitializationException {
    log.debug("doInitialize()");
    try {
      try {
	//secretId = StringSupport.trimOrNull(System.getenv("SEALER_KEY_SECRET_ID"));
	Constraint.isNotNull(secretId, "Secret ID cannot be null");
      } catch (final ConstraintViolationException e) {
	throw new ComponentInitializationException(e);
      }

      updateDefaultKey();
    
    } catch (final KeyException e) {
      log.error("Error loading default key from secret ID '{}' {}", secretId, e.getMessage());
      throw new ComponentInitializationException("Exception loading the default key", e);
    }

    if (!updateInterval.isZero()) {
      updateTask = new TimerTask() {
	@Override
	public void run() {
	  try {
	    updateDefaultKey();
	  } catch (final KeyException e) {
		
	  }
	}
      };
      if (updateTaskTimer == null) {
	internalTaskTimer = new Timer(TimerSupport.getTimerName(this), true);
      } else {
	internalTaskTimer = updateTaskTimer;
      }
      internalTaskTimer.schedule(updateTask, updateInterval.toMillis(), updateInterval.toMillis());
    }
  }

  /** {@inheritDoc} */
  @Override
  protected void doDestroy() {
    if (updateTask != null) {
      updateTask.cancel();
      updateTask = null;
      if (updateTaskTimer == null) {
	internalTaskTimer.cancel();
      }
      internalTaskTimer = null;
    }
    super.doDestroy();
  }

@Nonnull public Pair<String,SecretKey> getDefaultKey() throws KeyException {
    log.debug("getDefaultKey()");
    ComponentSupport.ifNotInitializedThrowUninitializedComponentException(this);
        
    synchronized(this) {
      if (defaultKey != null) {
	return new Pair<>(currentVersion, defaultKey);
      }
      throw new KeyException("No key has been retrieved");
    }
  }
    
  /**
   * Update the loaded copy of the default key from SecretsManager.
   * 
   * @throws KeyException if the key cannot be updated
  */
  private void updateDefaultKey() throws KeyException {
    synchronized(this) {
      GetSecretValueResult getSecretValueResult = getSecretValueResult(null);
      final String newVersion = getSecretValueResult.getVersionId();

      if (currentVersion == null) {
	log.info("Loading initial default key: {}", newVersion);
      } else if (!currentVersion.equals(newVersion)) {
	log.info("Updating default key from {} to {}", currentVersion, newVersion);
      } else {
	log.debug("Default key version has not changed, still {}", currentVersion);
	return;
      }
                
      SecretKey secretKey = getSecretKeyFromGetSecretValueResult(getSecretValueResult);

      if (secretKey == null) {
	log.error("Key could not be retrieved");
	throw new KeyException("Key could not be retrieved on update");
      }

      defaultKey = secretKey;
      currentVersion = getSecretValueResult.getVersionId();
                
      log.info("Default key updated to {}", currentVersion);
    }
  }

  @Nonnull public SecretKey getKey(@Nonnull @NotEmpty final String versionId) throws KeyNotFoundException {
    synchronized(this) {
      log.debug("getKey() with secret id '{}' and version '{}'", secretId, versionId);

      if (defaultKey != null && versionId.equals(currentVersion)) {
	return defaultKey;
      }
            
      if(versionId.length() < 32 || versionId.length() > 64) {
	throw new KeyNotFoundException("Invalid version id length");
      }

      GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest().withSecretId(secretId)
	  .withVersionId(versionId);
      GetSecretValueResult getSecretValueResult = getSecretValueResult(versionId);

      SecretKey secretKey = getSecretKeyFromGetSecretValueResult(getSecretValueResult);
      if (secretKey == null) {
	throw new KeyNotFoundException("Key not found");
      }
      log.debug("getKey({}) returning SecretKey => Format: {}, Algorithm: {}, Encoded Value: {}", versionId, secretKey.getFormat(), secretKey.getAlgorithm(), new String(secretKey.getEncoded()));
      log.info("Retrieved secret key with version: {}", versionId);
      return secretKey;
    }
  }

  private AWSSecretsManager getClient() {
    return AWSSecretsManagerClientBuilder.defaultClient();
  }

  private GetSecretValueResult getSecretValueResult(String versionId) {
    log.debug("getSecretValueResult({})", versionId);
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
