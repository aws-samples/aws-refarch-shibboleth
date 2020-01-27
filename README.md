# AWS Reference Architecture - Shibboleth-IdP

## Introduction

This Shibboleth IdP reference architecture will deploy a fully functional, scalable, and containerized Shibboleth IdP.  This reference architecture includes rotation of IdP sealer keys, utilizing AWS Secrets Manager and AWS Lambda.

### Reference Architecture Components
These components will be deployed as part of the reference architecture CloudFormation template.

* A new VPC to host resources deployed by this reference architecture (optionally use an existing VPC)
* A new ECS cluster to host the Shibboleth IdP service (optionally Fargate or EC2)
* A new ECS service to host the Shibboleth IdP containers
* A new CodeCommit repository to hold the configuration for the Shibboleth IdP
* A new CodeBuild project to facilitate configuration of the Shibboleth IdP container image
* A new CodePipeline pipeline to facilitate automated container image building and deployment, based on CodeCommit updates
* A new Elastic Load Balancer (ELB) to distribute traffic to the Shibboleth IdP containers
* An HTTP listener on the ELB for incoming traffic to the Shibboleth IdP
* An HTTPS listener for the ELB (optional, requires an ACM Certificate)
* Automatic sealer key rotation, facilitated by AWS Lambda and AWS Secrets Manager

## Deployment Procedure

### Deployment Overview
The following procedure will walk you through deploying a containerized Shibboleth IdP, along with a CI/CD pipeline.  The high level steps of the process are:
* Deploy the CloudFormation template for your infrastructure, including CI/CD pipeline
* Update the desired task count
* (Optional) Clone the newly created CodeCommit repo to your development machine
* (Optional) Push your completed code base to the CodeCommit repo
* Test the IdP functionality

### Launching your CloudFormation stack

Launch the CloudFormation stack using your AWS account by selecting an AWS Region below.  The template has two options: one that includes a VPC and one that uses an existing VPC.

#### Deploy VPC

| AWS Region Code | Name | Launch |
| --- | --- | --- 
| us-east-1 |US East (N. Virginia)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-east-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-east-1.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| us-east-2 |US East (Ohio)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-east-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-east-2.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| eu-west-1 |EU (Ireland)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-west-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-west-1.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| us-west-2 |US West (Oregon)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-west-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-west-2.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| ap-southeast-2 |AP (Sydney)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=ap-southeast-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-ap-southeast-2.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| eu-central-1 |EU (Frankfurt)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-central-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-eu-central-1.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |
| eu-west-2 |EU (London)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-west-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-eu-west-2.s3.amazonaws.com/aws-shibboleth-idp-withvpc.yaml) |

#### Use existing VPC

| AWS Region Code | Name | Launch |
| --- | --- | --- 
| us-east-1 |US East (N. Virginia)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-east-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-east-1.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| us-east-2 |US East (Ohio)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-east-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-east-2.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| eu-west-1 |EU (Ireland)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-west-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-west-1.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| us-west-2 |US West (Oregon)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=us-west-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-us-west-2.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| ap-southeast-2 |AP (Sydney)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=ap-southeast-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-ap-southeast-2.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| eu-central-1 |EU (Frankfurt)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-central-1#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-eu-central-1.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |
| eu-west-2 |EU (London)| [![cloudformation-launch-stack](images/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home?region=eu-west-2#/stacks/new?stackName=ShibIDP&templateURL=https://aws-refarch-shibboleth-eu-west-2.s3.amazonaws.com/aws-shibboleth-idp-novpc.yaml) |


### Update the desired task count

Since there is a bit of a chicken and egg situation with the cloud formation stacks, we initially deployed our ECS Service with a desired task count of 0.  Now that we have a properly built and functioning container and deployment pipeline, we will want to set that desired count to our actual desired count for production.

* Open the ECS service in the AWS console and find the cluster deployed by this CloudFormation stack (the name will begin with the name of the CloudFormation stack).
* Click on the cluster that was deployed by the CloudFormation stack .
* Then click on the service located under the Services tab (there should be only one).
* Now, click the "Update" button and change the "Number of tasks" field to 1 (or more).
* Then, click "Skip to review" followed by "Update Service".  This should cause the container to be properly deployed.


### (Optional) Clone the newly created CodeCommit repo to your development machine

Next, you will need to grab the URL of the repository you just created from the AWS console by choosing "Services" from the drop down menu.  Then, type in "CodeCommit" into the search box.  Next, click "Repositories" and subsequently the name of the repository that you specified in the CloudFormation template.  From this page, click on the "Copy" button under "Step 3: Clone the repository".

Then, run the following code in a terminal on your devlopment machine.  You will want to do this from the directory where you intend to checkout your newly created source repository.  Note that this step requires CodeCommit HTTPS credentials.  If you do not have these configured, you can do so in the IAM console by editing your IAM user account.

~~~~
<COMMAND YOU COPIED TO THE CLIPBOARD>
cd <NAME OF YOUR REPO>
~~~~

### (Optional) Push your code to CodeCommit

Finally, copy in one last config file, and then check in your local changes and push them up to your AWS CodeCommit repo.

~~~~
git add -A
git commit -a -m 'Initial check in'
git push origin master
~~~~

### Test the IdP functionality

Locate the root CloudFormation stack that was deployed for this reference architecture (note that there are nested stacks, but we are looking for the root stack).  Under the Outputs tab, locate the ServiceUrl.  If you have chosen to deploy the reference architecture using the optional HTTPS listener, then you can click on the link to open the IdP in your web browser.  If you have not chosen to deploy the HTTPS listener, you will need to copy the link to your clipboard, paste the URL into your web browser, and remote the 's' from the 'https://' URL prefix.

When the page is loaded, you should see a screen that says 'Our Identity Provider (replace this placeholder with your organizational logo/label)'.  If you get an error that says '503 Service Temporarily Unavailable', then you probably have no tasks running in the ECS service.  See the above instructions to update the desired task count of your ECS service.

## Customizing your IdP

In order for this deployment to become a functioning Shibboleth IdP, you will need to add your IdP configuration files to the container image.

* Complete the optional steps above to clone the new CodeCommit repo to your local machine
* Add your Shibboleth IdP configuration files to the repo folder on your machine
* Modify the Dockerfile in the repo to add your configuration files (you can utilize the commands at the end of the Dockerfile which have been commented out)
* Check in your changes and push to CodeCommit
* Once you have pushed your changes to CodeCommit, a new build of the IdP image should initiate automatically
* After the new IdP image is complete, you can force deployment of the new image by updating the service in ECS and selecting the 'Force new deployment' checkbox on the first page of the update service wizard.

## Conclusion

At this point, you have successfully deployed the latest Internet2 Shibboleth IdP container with integration of AWS Secrets Manager for the sealer key.  This is leveraging an AWS CodeCommit repo and have a CI/CD pipeline configured to build and deploy your version of the Shibboleth-IdP container.  To make changes, just edit your copy of the Dockerfile and check in your changes.  The CI/CD pipeline will automatically kick off a new build for you!
