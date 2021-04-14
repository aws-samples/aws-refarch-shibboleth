# Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

import boto3
import logging
import os
import secrets
import re
import sys
from pprint import pformat

logger = logging.getLogger()
logger.setLevel(logging.INFO)

def sort_by_tag(version_tags):
  matches = [string for string in version_tags[1] if re.match('AWSPREVIOUS-CUSTOM_\d+', string)]
  if len(matches) > 0:
    custom_number = re.search('AWSPREVIOUS-CUSTOM_(\d+)', matches[0])
    return int(custom_number.group(1))+2
  elif "AWSCURRENT" in version_tags[1] or "AWSPENDING" in version_tags[1]:
    return 1
  elif "AWSPREVIOUS" in version_tags[1]:
    return 2
  else:
    return sys.maxsize

def reversed_enumerate(collection):
    return zip(reversed(range(len(collection))), reversed(collection))

def lambda_handler(event, context):
    """Secrets Manager Rotation Template

    This is a template for creating an AWS Secrets Manager rotation lambda

    Args:
        event (dict): Lambda dictionary of event parameters. These keys must include the following:
            - SecretId: The secret ARN or identifier
            - ClientRequestToken: The ClientRequestToken of the secret version
            - Step: The rotation step (one of createSecret, setSecret, testSecret, or finishSecret)

        context (LambdaContext): The Lambda runtime information

    Raises:
        ResourceNotFoundException: If the secret with the specified arn and stage does not exist

        ValueError: If the secret is not properly configured for rotation

        KeyError: If the event parameters do not contain the expected keys

    """
    arn = event['SecretId']
    token = event['ClientRequestToken']
    step = event['Step']

    # Setup the client
    service_client = boto3.client('secretsmanager', endpoint_url=os.environ['SECRETS_MANAGER_ENDPOINT'])

    # Make sure the version is staged correctly
    metadata = service_client.describe_secret(SecretId=arn)
    if not metadata['RotationEnabled']:
        logger.error("Secret %s is not enabled for rotation" % arn)
        raise ValueError("Secret %s is not enabled for rotation" % arn)
    versions = metadata['VersionIdsToStages']
    if token not in versions:
        logger.error("Secret version %s has no stage for rotation of secret %s." % (token, arn))
        raise ValueError("Secret version %s has no stage for rotation of secret %s." % (token, arn))
    if "AWSCURRENT" in versions[token]:
        logger.info("Secret version %s already set as AWSCURRENT for secret %s." % (token, arn))
        return
    elif "AWSPENDING" not in versions[token]:
        logger.error("Secret version %s not set as AWSPENDING for rotation of secret %s." % (token, arn))
        raise ValueError("Secret version %s not set as AWSPENDING for rotation of secret %s." % (token, arn))

    if step == "createSecret":
        create_secret(service_client, arn, token)

    elif step == "setSecret":
        set_secret(service_client, arn, token)

    elif step == "testSecret":
        test_secret(service_client, arn, token)

    elif step == "finishSecret":
        finish_secret(service_client, arn, token)

    else:
        raise ValueError("Invalid step parameter")


def create_secret(service_client, arn, token):
    """Create the secret

    This method first checks for the existence of a secret for the passed in token. If one does not exist, it will generate a
    new secret and put it with the passed in token.

    Args:
        service_client (client): The secrets manager service client

        arn (string): The secret ARN or other identifier

        token (string): The ClientRequestToken associated with the secret version

    Raises:
        ResourceNotFoundException: If the secret with the specified arn and stage does not exist

    """
  
    metadata = service_client.describe_secret(SecretId=arn)
    sorted_versions = sorted(metadata["VersionIdsToStages"].items(),key=sort_by_tag)
    logger.info("createSecret: initial versions %s" % pformat(sorted_versions))
    
    # Make sure the current secret exists
    try:
      service_client.get_secret_value(SecretId=arn, VersionStage="AWSCURRENT")
    except service_client.exceptions.ResourceNotFoundException:
      logger.info("createSecret: NO CURRENT VERSION FOUND, CREATING ONE")
      service_client.put_secret_value(SecretId=arn, ClientRequestToken=token, SecretBinary=secrets.token_bytes(16), VersionStages=['AWSCURRENT'])

    # Now try to get the secret version, if that fails, put a new secret
    try:
        service_client.get_secret_value(SecretId=arn, VersionId=token, VersionStage="AWSPENDING")
        logger.info("createSecret: Successfully retrieved secret for %s." % arn)
    except service_client.exceptions.ResourceNotFoundException:
        # Put the secret
        service_client.put_secret_value(SecretId=arn, ClientRequestToken=token, SecretBinary=secrets.token_bytes(16), VersionStages=['AWSPENDING'])
        logger.info("createSecret: Successfully put secret for ARN %s and version %s." % (arn, token))

    metadata = service_client.describe_secret(SecretId=arn)
    sorted_versions = sorted(metadata["VersionIdsToStages"].items(),key=sort_by_tag)
    logger.info("createSecret: final versions %s" % pformat(sorted_versions))

def set_secret(service_client, arn, token):
    """Set the secret

    This method should set the AWSPENDING secret in the service that the secret belongs to. For example, if the secret is a database
    credential, this method should take the value of the AWSPENDING secret and set the user's password to this value in the database.

    Args:
        service_client (client): The secrets manager service client

        arn (string): The secret ARN or other identifier

        token (string): The ClientRequestToken associated with the secret version

    """


def test_secret(service_client, arn, token):
    """Test the secret

    This method should validate that the AWSPENDING secret works in the service that the secret belongs to. For example, if the secret
    is a database credential, this method should validate that the user can login with the password in AWSPENDING and that the user has
    all of the expected permissions against the database.

    Args:
        service_client (client): The secrets manager service client

        arn (string): The secret ARN or other identifier

        token (string): The ClientRequestToken associated with the secret version

    """
    # This is where the secret should be tested against the service
    current = service_client.get_secret_value(SecretId=arn, VersionStage="AWSPENDING")
    if len(current['SecretBinary']) == 16:
      logger.info("testSecret: Tested succesfully with version %s for secret %s." % (token, arn))
    else:
      raise ValueError("Rotation test failed")


def finish_secret(service_client, arn, token):
    """Finish the secret

    This method finalizes the rotation process by marking the secret version passed in as the AWSCURRENT secret.

    Args:
        service_client (client): The secrets manager service client

        arn (string): The secret ARN or other identifier

        token (string): The ClientRequestToken associated with the secret version

    Raises:
        ResourceNotFoundException: If the secret with the specified arn does not exist

    """
    metadata = service_client.describe_secret(SecretId=arn)
    sorted_versions = sorted(metadata["VersionIdsToStages"].items(),key=sort_by_tag)
    logger.info("finishSecret: initial versions %s" % pformat(sorted_versions))
    # First describe the secret to get the current version
    metadata = service_client.describe_secret(SecretId=arn)
    current_version = None
    for version in metadata["VersionIdsToStages"]:
        if "AWSCURRENT" in metadata["VersionIdsToStages"][version]:
            if version == token:
                # The correct version is already marked as current, return
                logger.info("finishSecret: Version %s already marked as AWSCURRENT for %s" % (version, arn))
                return
            current_version = version
            break

    # Finalize by staging the secret version current
    service_client.update_secret_version_stage(SecretId=arn, VersionStage="AWSCURRENT", MoveToVersionId=token, RemoveFromVersionId=current_version)
    logger.info("finishSecret: Successfully set AWSCURRENT stage to version %s for secret %s.  Removed AWSCURRENT from version %s." % (token, arn, current_version))
    # Bump all the previous versions as required by the configuration
    metadata = service_client.describe_secret(SecretId=arn)
    sorted_versions = sorted(metadata["VersionIdsToStages"].items(),key=sort_by_tag)
    max_versions = int(os.environ['SEALER_KEY_VERSION_COUNT'])
    while (len(sorted_versions) > max_versions):
        prune_version_id = sorted_versions[len(sorted_versions)-1][0]
        prune_version_stage = sorted_versions[len(sorted_versions)-1][1][0]
        service_client.update_secret_version_stage(SecretId=arn, VersionStage=prune_version_stage, RemoveFromVersionId=prune_version_id)
        del sorted_versions[-1]
        logger.info("finishSecret: REMOVED Stage %s from version %s" % (prune_version_stage, prune_version_id))
    for offset, version in reversed_enumerate(sorted_versions, start=1):
      version_stage = "AWSPREVIOUS-CUSTOM_"+str(offset)
      if((offset < len(sorted_versions)-1) and (offset < max_versions) and (len(sorted_versions) > offset-1) and (version_stage in sorted_versions[offset+1][1])):
        service_client.update_secret_version_stage(SecretId=arn, VersionStage=version_stage, MoveToVersionId=sorted_versions[offset][0], RemoveFromVersionId=sorted_versions[offset+1][0])
        logger.info("finishSecret: Successfully set %s stage to version %s for secret %s.  Removed %s from version %s." % (version_stage, sorted_versions[offset][0], arn, version_stage, sorted_versions[offset+1][0]))
      elif(offset < len(sorted_versions)):
        service_client.update_secret_version_stage(SecretId=arn, VersionStage=version_stage, MoveToVersionId=sorted_versions[offset][0])
        logger.info("finishSecret: Successfully set %s stage to version %s for secret %s." % (version_stage, sorted_versions[offset][0], arn))
    metadata = service_client.describe_secret(SecretId=arn)
    sorted_versions = sorted(metadata["VersionIdsToStages"].items(),key=sort_by_tag)
    logger.info("finishSecret: final versions %s" % pformat(sorted_versions))
