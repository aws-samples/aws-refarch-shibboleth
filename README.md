# AWS Reference Architecture - Shibboleth-IdP

## Introduction

* This Shibboleth IdP reference architecture will deploy a fully functional, scalable, and containerized Shibboleth IdP.  This reference architecture includes rotation of IdP sealer keys, utilizing AWS Secrets Manager and AWS Lambda. In addition, the certificates that are part of the IdP as well as some of the LDAP settings (including the username/password) are stored in AWS Secrets Manager.
* This project is intended to be a starting point for getting the Shibboleth IdP up and running quickly and easily on AWS and provide the foundation to build a production ready deployment around.  Be aware that if you do delete the stack, it will delete your CodeCommit repository so your customizations will be lost.  Therefore, if you intend to use this for production, it would be a good idea to make a copy of the repo and host it in your own account and take precautions to safeguard your changes

### Reference Architecture Components
These components will be deployed as part of the reference architecture CloudFormation template.

* A new VPC to host resources deployed by this reference architecture (optionally use an existing VPC)
* A new ECS cluster to host the Shibboleth IdP service (optionally Fargate or EC2)
* A new ECS service to host the Shibboleth IdP containers
* A new CodeCommit repository to hold the configuration for the Shibboleth IdP
* A new CodeBuild project to facilitate configuration of the Shibboleth IdP container image
* A new CodePipeline pipeline to facilitate automated container image building and deployment, based on CodeCommit updates
* A new Elastic Load Balancer (ELB) to distribute traffic to the Shibboleth IdP containers
* An HTTP listener on the ELB on port 80 that redirects to 443
* An HTTPS listener on the ELB for incoming traffic to the Shibboleth IdP
* Automatic sealer key rotation, facilitated by AWS Lambda and AWS Secrets Manager

## Assumptions/Prerequisites

* It is expected that you know how to update your DNS records to point your fully qualified domain name for your IdP to the load balancer that is created as part of the stack.
* You must have the ARN for a pre-created certificate in Amazon Certificate Manager that corresponds to the fully qualified domain name of your IdP
* Since most setups leverage an LDAP server as the identity store, there are some LDAP specific parameters required to launch the stack.  If this describes your situation, you will need to have those settings handy.  If you are not using an LDAP server, you can just leave those set to the default values but you will need to manually configure your identity store after launching the stack which is outside of the scope of this project.

## Deployment Procedure

### Deployment Overview
The following procedure will walk you through deploying a containerized Shibboleth IdP, along with a CI/CD pipeline.  The high level steps of the process are:
* Deploy the CloudFormation template for your infrastructure, including CI/CD pipeline
* Update the desired task count
* Test the IdP functionality
* (Optional) Cloning the newly created CodeCommit repo to your development machine
* (Optional) Customizing your IdP
* (Optional) Adding support for service providers to your IdP
* (Optional) Adding AWS SSO Support to your IdP

### Launching your CloudFormation stack

Launch the CloudFormation stack using your AWS account by selecting an AWS Region below.  The template has two options: one that includes a VPC and one that uses an existing VPC.

#### Deploy VPC

| AWS Region Code | Name | Launch |
| --- | --- | --- 
| us-east-1 |US East (N. Virginia)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-east-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-east-1.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| us-east-2 |US East (Ohio)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-east-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-east-2.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| us-west-1 |US West (N. California)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-west-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-west-1.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| us-west-2 |US West (Oregon)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-west-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-west-2.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| ap-southeast-2 |AP (Sydney)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=ap-southeast-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-ap-southeast-2.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| eu-central-1 |EU (Frankfurt)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-central-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-eu-central-1.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| eu-west-2 |EU (London)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-west-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-eu-west-2.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |

#### Use existing VPC

| AWS Region Code | Name | Launch |
| --- | --- | --- 
| us-east-1 |US East (N. Virginia)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-east-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-east-1.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| us-east-2 |US East (Ohio)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-east-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-east-2.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| us-west-1 |US West (N. California)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-west-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-west-1.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| us-west-2 |US West (Oregon)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-west-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-west-2.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| ap-southeast-2 |AP (Sydney)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=ap-southeast-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-ap-southeast-2.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| eu-central-1 |EU (Frankfurt)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-central-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-eu-central-1.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| eu-west-2 |EU (London)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-west-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-eu-west-2.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |

### Update DNS
Locate the root CloudFormation stack that was deployed for this reference architecture (note that there are nested stacks, but we are looking for the root stack).  Under the Outputs tab, locate the LoadBalancerDNSName entry.  You need to update your DNS entry for the fully qualified domain name you supplied when you launched the stack to be a CNAME that points to the LoadBalancerDNSName shown.


### Update the desired task count

Since there is a bit of a chicken and egg situation with the cloud formation stacks, we initially deployed our ECS Service with a desired task count of 0.  Now that we have a properly built and functioning container and deployment pipeline, we will want to set that desired count to our actual desired count for production.

* Open the ECS service in the AWS console and find the cluster deployed by this CloudFormation stack (the name will begin with the name of the CloudFormation stack).
* Click on the cluster that was deployed by the CloudFormation stack .
* Then click on the service located under the Services tab (there should be only one).
* Now, click the "Update" button and change the "Number of tasks" field to 1 (or more).
* Then, click "Skip to review" followed by "Update Service".  This should cause the container to be properly deployed.

### Test the IdP functionality

Locate the root CloudFormation stack that was deployed for this reference architecture (note that there are nested stacks, but we are looking for the root stack).  Under the Outputs tab, locate the ServiceUrl.  You can click on the link to open the IdP in your web browser.

When the page is loaded, you should see a screen that says 'Our Identity Provider (replace this placeholder with your organizational logo/label)'.  If you get an error that says '503 Service Temporarily Unavailable', then you probably have no tasks running in the ECS service.  See the above instructions to update the desired task count of your ECS service.

<img src="images/unconfigured-idp-screenshot.png" width="50%">

## Cloning the newly created CodeCommit repo to your development machine

Next, you will need to grab the URL of the repository you just created from the AWS console by choosing "Services" from the drop down menu.  Then, type in "CodeCommit" into the search box.  Next, click "Repositories" and subsequently the name of the repository that you specified in the CloudFormation template.  From this page, click on the "Clone URL" button followed by "Clone HTTPS".

Then, run the following code in a terminal on your devlopment machine.  You will want to do this from the directory where you intend to checkout your newly created source repository.  Note that this step requires CodeCommit HTTPS credentials.  If you do not have these configured, you can do so in the IAM console by editing your IAM user account.

~~~~
git clone <URL YOU COPIED TO THE CLIPBOARD>
<ENTER USERNAME FROM IAM GIT CREDENTIALS>
<ENTER PASSWORD FROM IAM GIT CREDENTIALS>
~~~~

## Customizing your IdP

In order for this deployment to become a functioning Shibboleth IdP, you will need to add your IdP configuration files to the container image. In general, the steps required to complete this are as follows but see below for specific instructions for some common integrations:

* Complete the steps above to clone the new CodeCommit repo to your local machine
* Add your Shibboleth IdP configuration files to the repo folder on your machine
* Modify the Dockerfile in the repo to add your configuration files
* Check in your changes and push to CodeCommit
* Once you have pushed your changes to CodeCommit, a new build of the IdP container should initiate automatically. You can use the CodePipeline section of the AWS Console to monitor the status of the build and depoyment.  Once the "Deploy" step has completed successfully, your changes should be available in your IdP.

## Customize the branding of your IdP

If you want to change the look and feel of the IdP's web pages, you will follow the general guidelines above for customizing your IdP.  Specific to the branding, you can make modifications to the CSS files in *config/shib-idp/edit-webapp/css/* folder of your CodeCommit repository.  If you need to add or remove any images for those pages, they should be placed in the *config/shib-idp/edit-webapp/images/* of your CodeCommit repository.

## Using your own certificates for the IdP

If you already have an IdP running and would like to import your certificates into this IdP, or if you want to change the certificates that are created when the Docker container is built the first time for rotation purposes, you can do so.  There are three certificates that the IdP makes use of.  They are named:

* Signing
* Backchannel
* Encryption

Each certificate is stored in its own secret in AWS Secrets Manager as a JSON object that contains 2 key/value pairs.  The valid key names are:

* key
* cert

To modify these you must have access to the appropriate key and cert for the certificate you are trying to update.  If you would like to generate a new key and certificate you can do so by running the following command substituting your fully qualified domain name for 'sso.example.com':

~~~
/usr/bin/openssl req -new -x509 -nodes -newkey rsa:2048 -keyout ./my-key.pem -days 3650 -subj "/CN=sso.example.com" -out ./my-cert.pem
~~~

You then need to convert both the key and the cert to a one line string with the return lines escaped.  In the above example you could do this by running the following commands:

~~~
awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}' ./my-key.pem
awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}' ./my-cert.pem
~~~

The respective output for each of those commands would look something like this:

~~~
-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQChzPTGYsCIXJ6e\nz69gYVlY0PqZq7nHlYQNsxBUq+6zENcPmVgptZQsQhSB78u+7nGNBB+C2ZTEcm4+\nsGwkCl0LOevM38v80bJcP2e92+cY+MyIsCVh0/SBGZaQndf8oAlEPI6ieCtyyb7l\nw9XnaTYMUPrhMnHdBJf/MVcPY/Zkj0UKfyPE38joqqNSMXgLYUk1CVH1LbxHcQjz\nFEp1D+I6hLW0U5bVSKz+J0J/QkH0xaf8x/TisuqhSCWRUvpJmuAea5NVJp3qD68u\nX8JRTpIZUtd3ExTAvEHHEfOANispaMsrn8lNlsLcNkatlXTBq+JStkiIITHyy8d5\n/lv0Y81xAgMBAAECggEAb2OoiANoRPhugyq7tACRACIuxCiDJ7H5TW0fKwuKs2A+\njpClL2HAdo+vipzJidKh/dD+2JhuYKfe2e2fJVi1IYtomIzh5FWBExzbkMuZV4nB\nVcDem3DN/SreiwT/sv2CI6YFFDjiwI2/jHK8CipWeTgu32ZZGS2WbqtLIl9RO3yG\nSo6LpYn0x5Wlkf81iBYQAAfZ/SAwGReM5QFTADIki1D+K6YKEI8jf7V1XSzAEHJW\nEpNWzVbZW30fXgRAGreNIwliOcGfBTdS0WASyR6gx7ZtHn6YYANJC8uXZ2uENN9N\ne+IvIWibruDHzBiRs5mjXuP/UC+40eKYcl+HW9oNgQKBgQDSf1mlxJ/eKYo2sM9U\nCWqaPrNxaWeU9oir3s6ly9J3wTEq67vpoUSEzIkTz2AuWpDyAK8qV2aJvDF23Pel\nnbn8u50v6czc4GmDVTwilDw506ccjU7wZ0/05Ba2ntl/Lhmqh0Fl5PDgZIJthZYa\ndEiFmcKYteqZ7xO2BGq0b5A6CQKBgQDExslyflzLvxQvQygEzfsKZIl0KraiRqPD\nz0ZbrGB3O2/V7TBMODgy3hRmZBD7dVV7I90XwJFkqRbBy/lioqSkdTWxjirNidiC\nyvL2dveHrqfmsEdariBo/VPwQLPtyWFuCkmDeCDPwfMzT7DWQXn5KczaS7Xsmtrz\nO+gBd37yKQKBgDPb8Cgo4wIG1ehXcxzPHRZXMDpWr5BBlLZTNDIgnv+l9wkN/sO2\nWX0mLvdUdeu1oFQe2wjhc3+nf8A6/EAvNa0QQ8+UGEaqexRCIYibYKMWg+tC3RnZ\nOhepQfdH92mf3+7lV7VUzAtcd82nxa1KzLstgIXC+8Nk5URInklJ2wyRAoGBAKZf\neOWgJ29xUGyheE2PpsjjtkAM+Tv9Q+uD7fYGqc1F6MnUcHiRNBr2IANuadliHHMw\nIHg7rfKQ8JfMkT+vSktueoZ0MIZ2HSSA2ao/bgw/RODMfJgUD30Va4n/+uF62jDC\n6+MHh56BWtIwd9C/rZRYnkaXquqxB5yOMxxCX4f5AoGBALJLamxruNHY7iPCnkOK\nnVat+a7rJ/QyhM5fk4Oz0r3T8SdZe4N+5jmBi6l4DCE+4q0jUshroczbsCO7rKdD\nGjXsINT3wmxF8T1Xap/0kkU2GpvK8naZLme6ollFIldR88C6xL/S6va4Z5FExYbU\nN0HGBJGKZMaj762nD31RjnlB\n-----END PRIVATE KEY-----\n
~~~
~~~
-----BEGIN CERTIFICATE-----\nMIICsDCCAZgCCQDm1MJsQTRrLzANBgkqhkiG9w0BAQsFADAaMRgwFgYDVQQDDA9z\nc28uZXhhbXBsZS5jb20wHhcNMjAwNDMwMTYyMTU0WhcNMzAwNDI4MTYyMTU0WjAa\nMRgwFgYDVQQDDA9zc28uZXhhbXBsZS5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IB\nDwAwggEKAoIBAQChzPTGYsCIXJ6ez69gYVlY0PqZq7nHlYQNsxBUq+6zENcPmVgp\ntZQsQhSB78u+7nGNBB+C2ZTEcm4+sGwkCl0LOevM38v80bJcP2e92+cY+MyIsCVh\n0/SBGZaQndf8oAlEPI6ieCtyyb7lw9XnaTYMUPrhMnHdBJf/MVcPY/Zkj0UKfyPE\n38joqqNSMXgLYUk1CVH1LbxHcQjzFEp1D+I6hLW0U5bVSKz+J0J/QkH0xaf8x/Ti\nsuqhSCWRUvpJmuAea5NVJp3qD68uX8JRTpIZUtd3ExTAvEHHEfOANispaMsrn8lN\nlsLcNkatlXTBq+JStkiIITHyy8d5/lv0Y81xAgMBAAEwDQYJKoZIhvcNAQELBQAD\nggEBAC6M/FjsbuuGkizVT8wc9vlpS/oD5Sb5iA7rQO0eb44US6sEvglVuC2RTsHU\n/fIUYcLrZBSfbDBjiCIs1LRLsopFQObhGm/R2uyXcpHAIpFpIOPsHgcWY4T017dQ\n880NbMpGEHzgp8w2tRAivxsEeA0fQfGUZMAtiBmpUbib9y9Az8/QRM743k7xAEvF\n4s9s6hzw0Uva5gINhLHZdOtJFygbSXFD00MwgSUYPsvPWHz+OLVIvaptjsLLcnQE\nrHeop3FEeKIabAbRXFRQXATHA9LCeqCUBK5FrJuXA71oVN9g0+aR8cPzEinHGgo2\ng0KZhE4r8JoUZ5GeDhQworDdPfE=\n-----END CERTIFICATE-----\n
~~~

You would then go into the Secrets Manager section of the AWS Console and click on "Secrets".  From the list of secrets, you would choose the secret you want to update.  These will end with either *-signing*, *-backchannel*, or *-encryption* based on which one you are trying to update.  Once you've chosen one, go to the "Secret value" section and click "Retrieve secret value".  Then click "Edit".  In the dialog that pops up, switch to the "Plaintext" mode and paste in the output from the above commands into the value part of the appropriate key/value pairs (ie key or cert).  Make sure that you have double quotes surrounding the values you paste in or else the JSON data will not be properly formed.  Continuing with the above example, my full plaintext secret would look like this:

~~~
{
  "key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQChzPTGYsCIXJ6e\nz69gYVlY0PqZq7nHlYQNsxBUq+6zENcPmVgptZQsQhSB78u+7nGNBB+C2ZTEcm4+\nsGwkCl0LOevM38v80bJcP2e92+cY+MyIsCVh0/SBGZaQndf8oAlEPI6ieCtyyb7l\nw9XnaTYMUPrhMnHdBJf/MVcPY/Zkj0UKfyPE38joqqNSMXgLYUk1CVH1LbxHcQjz\nFEp1D+I6hLW0U5bVSKz+J0J/QkH0xaf8x/TisuqhSCWRUvpJmuAea5NVJp3qD68u\nX8JRTpIZUtd3ExTAvEHHEfOANispaMsrn8lNlsLcNkatlXTBq+JStkiIITHyy8d5\n/lv0Y81xAgMBAAECggEAb2OoiANoRPhugyq7tACRACIuxCiDJ7H5TW0fKwuKs2A+\njpClL2HAdo+vipzJidKh/dD+2JhuYKfe2e2fJVi1IYtomIzh5FWBExzbkMuZV4nB\nVcDem3DN/SreiwT/sv2CI6YFFDjiwI2/jHK8CipWeTgu32ZZGS2WbqtLIl9RO3yG\nSo6LpYn0x5Wlkf81iBYQAAfZ/SAwGReM5QFTADIki1D+K6YKEI8jf7V1XSzAEHJW\nEpNWzVbZW30fXgRAGreNIwliOcGfBTdS0WASyR6gx7ZtHn6YYANJC8uXZ2uENN9N\ne+IvIWibruDHzBiRs5mjXuP/UC+40eKYcl+HW9oNgQKBgQDSf1mlxJ/eKYo2sM9U\nCWqaPrNxaWeU9oir3s6ly9J3wTEq67vpoUSEzIkTz2AuWpDyAK8qV2aJvDF23Pel\nnbn8u50v6czc4GmDVTwilDw506ccjU7wZ0/05Ba2ntl/Lhmqh0Fl5PDgZIJthZYa\ndEiFmcKYteqZ7xO2BGq0b5A6CQKBgQDExslyflzLvxQvQygEzfsKZIl0KraiRqPD\nz0ZbrGB3O2/V7TBMODgy3hRmZBD7dVV7I90XwJFkqRbBy/lioqSkdTWxjirNidiC\nyvL2dveHrqfmsEdariBo/VPwQLPtyWFuCkmDeCDPwfMzT7DWQXn5KczaS7Xsmtrz\nO+gBd37yKQKBgDPb8Cgo4wIG1ehXcxzPHRZXMDpWr5BBlLZTNDIgnv+l9wkN/sO2\nWX0mLvdUdeu1oFQe2wjhc3+nf8A6/EAvNa0QQ8+UGEaqexRCIYibYKMWg+tC3RnZ\nOhepQfdH92mf3+7lV7VUzAtcd82nxa1KzLstgIXC+8Nk5URInklJ2wyRAoGBAKZf\neOWgJ29xUGyheE2PpsjjtkAM+Tv9Q+uD7fYGqc1F6MnUcHiRNBr2IANuadliHHMw\nIHg7rfKQ8JfMkT+vSktueoZ0MIZ2HSSA2ao/bgw/RODMfJgUD30Va4n/+uF62jDC\n6+MHh56BWtIwd9C/rZRYnkaXquqxB5yOMxxCX4f5AoGBALJLamxruNHY7iPCnkOK\nnVat+a7rJ/QyhM5fk4Oz0r3T8SdZe4N+5jmBi6l4DCE+4q0jUshroczbsCO7rKdD\nGjXsINT3wmxF8T1Xap/0kkU2GpvK8naZLme6ollFIldR88C6xL/S6va4Z5FExYbU\nN0HGBJGKZMaj762nD31RjnlB\n-----END PRIVATE KEY-----\n",
  "cert": "-----BEGIN CERTIFICATE-----\nMIICsDCCAZgCCQDm1MJsQTRrLzANBgkqhkiG9w0BAQsFADAaMRgwFgYDVQQDDA9z\nc28uZXhhbXBsZS5jb20wHhcNMjAwNDMwMTYyMTU0WhcNMzAwNDI4MTYyMTU0WjAa\nMRgwFgYDVQQDDA9zc28uZXhhbXBsZS5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IB\nDwAwggEKAoIBAQChzPTGYsCIXJ6ez69gYVlY0PqZq7nHlYQNsxBUq+6zENcPmVgp\ntZQsQhSB78u+7nGNBB+C2ZTEcm4+sGwkCl0LOevM38v80bJcP2e92+cY+MyIsCVh\n0/SBGZaQndf8oAlEPI6ieCtyyb7lw9XnaTYMUPrhMnHdBJf/MVcPY/Zkj0UKfyPE\n38joqqNSMXgLYUk1CVH1LbxHcQjzFEp1D+I6hLW0U5bVSKz+J0J/QkH0xaf8x/Ti\nsuqhSCWRUvpJmuAea5NVJp3qD68uX8JRTpIZUtd3ExTAvEHHEfOANispaMsrn8lN\nlsLcNkatlXTBq+JStkiIITHyy8d5/lv0Y81xAgMBAAEwDQYJKoZIhvcNAQELBQAD\nggEBAC6M/FjsbuuGkizVT8wc9vlpS/oD5Sb5iA7rQO0eb44US6sEvglVuC2RTsHU\n/fIUYcLrZBSfbDBjiCIs1LRLsopFQObhGm/R2uyXcpHAIpFpIOPsHgcWY4T017dQ\n880NbMpGEHzgp8w2tRAivxsEeA0fQfGUZMAtiBmpUbib9y9Az8/QRM743k7xAEvF\n4s9s6hzw0Uva5gINhLHZdOtJFygbSXFD00MwgSUYPsvPWHz+OLVIvaptjsLLcnQE\nrHeop3FEeKIabAbRXFRQXATHA9LCeqCUBK5FrJuXA71oVN9g0+aR8cPzEinHGgo2\ng0KZhE4r8JoUZ5GeDhQworDdPfE=\n-----END CERTIFICATE-----\n"
}
~~~
### Adding support for service providers to your IdP

This solutions leverages the LocalDynamicMetadataProvider option for configuring service providers.  You can read more about how this works at https://wiki.shibboleth.net/confluence/display/IDP4/LocalDynamicMetadataProvider.  In order configure a new service provider, you would need to add metadata for the service provider to the “config/shib-idp/metadata/sp” directory of your repo.  You would then check the file in and push it to trigger a new deployment.  For an example of how this can be done, see the next section which discusses adding support for AWS SSO support.  The last section of steps in that go into a real-world example of how this can be done. 

### Adding AWS SSO Support to your IdP

* Go to console and click “Enable AWS SSO” button
* In the “User portal” section, customize the “User portal URL” as appropriate (make sure to do this first as some of the subsequent values will change).
* Click step 1 “Choose your identity source”
* In the "Identity source" section, Next to “Indentity source“ entry, click “Change”
* Choose “External identity provider”
* Next to “AWS SSO SAML metadata”, click “Download metadata file” and save it into your checked out repo at the path config/shib-idp/metadata/sp.  We will rename the file later after we compute the proper sha1 name for it.
* Click on the link that says “If you don't have a metadata file, you can manually type your metadata values”.
* In the “IdP sign-in URL” field enter: https://*fully qualified domain for idp*/idp/profile/SAML2/POST/SSO (make sure to substitute your IdP's FQDN in the url)
* In the “IdP issuer URL” field enter: https://*fully qualified domain for idp*/idp/shibboleth (make sure to substitute your IdP's FQDN in the url)
* In a new browser tab, visit the same link you just entered into the “IdP issuer URL” field and then view the page source.  Scroll down to the second X509Certificate XML element and copy the certificate data.
* Paste that data into a file such as “shibboleth.xml” and save it.
* In the “IdP certificate“ field, click the ”Browse...” button and choose the file you just created.
* Click the “Next: Review” button.
* Review the changes and follow the instructions if you accept them
* Back on your local machine, in a terminal, make sure you are in the “config/shib-idp/metadata/sp” directory of your repo.
* View the contents of the file you downloaded earlier and look for the field named “entityID”.  It should contain a URL similar to “https://us-east-1.signin.aws.amazon.com/platform/saml/d-90670f2e63“.   Copy that url and then run the command:
    * echo -n "https://us-east-1.signin.aws.amazon.com/platform/saml/d-90670f2e63" | openssl sha1 (but replace the url with the one you just copied).
    * The output of this command should be something like: 202eae420fda1d65c5c608032bc1add15650261b
    * That sha1 code is what you need to rename your file.  So, in this example. I would rename the file to 202eae420fda1d65c5c608032bc1add15650261b.xml
    * Add this file to your repo and check it in (this will trigger your pipeline to run and in a few minutes, your configuration for AWS sso should be complete).  Once you see that the “Deploy” phase back in the “CodePipeline” section of the AWS console has finished, you can test the integration by going to the “User portal URL” shown in the “Settings” section of of the “AWS SSO” service in the AWS console.  If everything worked properly, you should see a login page being served up from your IdP.

### Updating LDAP Settings

Should you need to update any LDAP settings there are two different possible places you should handle this.  First, there are four LDAP configuration settings that are stored in AWS Secrets Manager.  Those are the values that you supplied as part of the initial CloudFormation stack you launched.  Specifically they are:
* LDAP URL
* Base DN
* Read-only user name for connecting to the LDAP server
* Read-only user password for connecting to the LDAP server
If you need to change any of these, please do so by modifying them via AWS Secrets Manager.  These settings are stored in a secret that ends in *-ldap-setting*.

If you need to change any other LDAP properties you should do so in the *config/shib-idp/conf/ldap.properties* file that is part of the CodeCommit repository and check in your changes.  This will trigger the CodePipeline to build and redeploy the container for the IdP.

Note: Within the *config/shib-idp/conf/ldap.properties* file, the settings associated with the LDAP settings stored in AWS Secrets Manager have values set to a pattern using the formula *XXXX-<SETTING NAME>-XXXX*.  If you modify these values, this will prevent the values stored in AWS Secrets Manager from having any effect.  As part of the Docker build process the values are pulled from AWS Secrets Manager and substituted into the *config/shib-idp/conf/ldap.properties* file and that logic will only work if the appropriate setting pattern is present.

### Modifying the SAML Payload

Along the same lines as the *Note* listed above, the domain that is configured to be used as the email address domain by default within the SAML payload is set to the base domain you enter at stack creation time.  If that does not work for your use case, you can modify the *config/shib-idp/conf/ldap.properties* file in your repo and set the *mail* attribute to whatever works for you.  By default it replaces the value of the *XXXX-ParentDomain-XXXX* string but if you manually change that it will not overwrite your setting as the pattern it is searching for won't be found.

If ther are other customizations you need to make to the SAML payload, those will generally be done by making modifications some combination of the following files:

* *config/shib-idp/conf/attribute-filter.xml*
* *config/shib-idp/conf/attribute-resolver.xml*


### Troubleshooting

## LDAP Issues

The first place to look for errors related to the LDAP configuration is in the Amazon CloudWatch Logs.  The logs are associated with the ECS container(s) that the Shibboleth IdP is contained in.  To find those, log into the AWS Management Console and go to the "CloudWatch" section.  Under "Logs", click on "Log groups". in the "Filter" field type "/ecs" and press return.  This should show you the list of Log Groups for the ECS service.  In there should be a log Group named after your IdP stack.  Click on the name of that Log Group.  If you are running multiple IdP containers then the logs of interest could be in multiple "Log Streams".  Find the one of interest to you and click on it.  You should then be able to log through those logs to find any errors.

Another recommendation if you are getting LDAP errors is to verify that your LDAP settings are correct.  An easy way to do this from a linux instance with access to the LDAP server is to run

~~~
ldapsearch -H <ldap_url> -x -W -D "<read_only_user>" -b "<base_dn>"
~~~

from the command line, substituting the values that are stored in the *<stack_name>-ldap-settings* secret in AWS Secrets Manager.  Do not include the <> symbols.  When prompted for your password, use the *read_only_password* setting that is also stored in the secret.  If you don't get back a list of objects (including the user object you are trying to login with), then you may have an issue with your LDAP settings.

## AWS SSO Issues

If, after you login to the IdP, you get redirected and see an error such as the following

<img src="images/incorrect-cert-screenshot.png" width="50%">

then you may not have used the correct certificate (second one) from the https://*fully qualified domain for idp*/idp/shibboleth page.  Double check that you did and re-enter it in the AWS SSO configuration if needed. Another possible cause is if the email address for your user doesn't match that in the ldap server.  In this case, make sure that the parent domain you supplied when you launched the stack matches your email domain.

### Cleanup

There are a couple of manual steps you will need to undertake before you can delete the base stack.  First, you will need to empty the bucket where the artifacts from your CodePipeline executions reside and you will need to delete all the images from your Elastic Container Registry's Repository.  Once those are done, then you can proceed with deleting the base stack via the CloudFormation console.

### Conclusion

At this point, you have successfully deployed the latest Internet2 Shibboleth IdP container with integration of AWS Secrets Manager for the sealer key.  This is leveraging an AWS CodeCommit repo and have a CI/CD pipeline configured to build and deploy your version of the Shibboleth-IdP container.  To make changes, just edit your copy of the Dockerfile and check in your changes.  The CI/CD pipeline will automatically kick off a new build for you!
