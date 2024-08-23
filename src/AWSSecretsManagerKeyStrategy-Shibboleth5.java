import java.security.KeyException;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.SecretKey;

import org.slf4j.Logger;

import net.shibboleth.shared.annotation.constraint.NonNegative;
import net.shibboleth.shared.annotation.constraint.NonnullAfterInit;
import net.shibboleth.shared.annotation.constraint.NotEmpty;
import net.shibboleth.shared.collection.Pair;
import net.shibboleth.shared.component.AbstractInitializableComponent;
import net.shibboleth.shared.component.ComponentInitializationException;
import net.shibboleth.shared.component.ComponentSupport;
import net.shibboleth.shared.logic.Constraint;
import net.shibboleth.shared.logic.ConstraintViolationException;
import net.shibboleth.shared.primitive.LoggerFactory;
import net.shibboleth.shared.primitive.TimerSupport;
import net.shibboleth.shared.scripting.EvaluableScript;
import net.shibboleth.shared.security.DataSealerKeyStrategy;

import javax.crypto.spec.SecretKeySpec;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

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
  public AWSSecretsManagerKeyStrategy() {
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
      GetSecretValueResponse getSecretValueResponse = getSecretValueResponse(null);
      final String newVersion = getSecretValueResponse.versionId();

      if (currentVersion == null) {
	log.info("Loading initial default key: {}", newVersion);
      } else if (!currentVersion.equals(newVersion)) {
	log.info("Updating default key from {} to {}", currentVersion, newVersion);
      } else {
	log.debug("Default key version has not changed, still {}", currentVersion);
	return;
      }
                
      SecretKey secretKey = getSecretKeyFromGetSecretValueResponse(getSecretValueResponse);

      if (secretKey == null) {
	log.error("Key could not be retrieved");
	throw new KeyException("Key could not be retrieved on update");
      }

      defaultKey = secretKey;
      currentVersion = getSecretValueResponse.versionId();
                
      log.info("Default key updated to {}", currentVersion);
    }
  }

  @Nonnull public SecretKey getKey(@Nonnull @NotEmpty final String versionId) throws KeyException {
    synchronized(this) {
      log.debug("getKey() with secret id '{}' and version '{}'", secretId, versionId);

      if (defaultKey != null && versionId.equals(currentVersion)) {
	return defaultKey;
      }
            
      if(versionId.length() < 32 || versionId.length() > 64) {
        log.warn("getKey({}) invalid version id length", versionId);
	throw new KeyException("Invalid version id length");
      }

      GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretId).versionId(versionId).build();
      GetSecretValueResponse getSecretValueResponse = getSecretValueResponse(versionId);

      SecretKey secretKey = getSecretKeyFromGetSecretValueResponse(getSecretValueResponse);
      if (secretKey == null) {
        log.warn("getKey({}) key with specified version id not found", versionId);
	throw new KeyException("Key not found");
      }
      log.debug("getKey({}) returning SecretKey => Format: {}, Algorithm: {}, Encoded Value: {}", versionId, secretKey.getFormat(), secretKey.getAlgorithm(), new String(secretKey.getEncoded()));
      log.info("Retrieved secret key with version: {}", versionId);
      return secretKey;
    }
  }

  private SecretsManagerClient getClient() {
    return SecretsManagerClient.builder().build();
  }

  private GetSecretValueResponse getSecretValueResponse(String versionId) {
    log.debug("getSecretValueResponse({})", versionId);
    SecretsManagerClient client = getClient();

    GetSecretValueRequest.Builder getSecretValueRequestBuilder = GetSecretValueRequest.builder().secretId(secretId);
    if (versionId != null) {
      getSecretValueRequestBuilder.versionId(versionId);
    }
    try {
      return client.getSecretValue(getSecretValueRequestBuilder.build());
    } catch (SecretsManagerException e) {
      log.error("The requested secret {} was not found", secretId);
      log.error(e.awsErrorDetails().errorMessage());
    }
    return null;
  }

  private SecretKey getSecretKeyFromGetSecretValueResponse(GetSecretValueResponse getSecretValueResponse) {
    SecretKey secretKey = null;
    //In addition to null values, exclude old versions that have been deprecated (all stage labels removed).
    if (getSecretValueResponse != null && getSecretValueResponse.versionStages() != null && getSecretValueResponse.versionStages().size() > 0) {
      byte[] arr = getSecretValueResponse.secretBinary().asByteArray();
      secretKey = new SecretKeySpec(arr, 0, arr.length, "AES");
    }
    return secretKey;
  }
}
