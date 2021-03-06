<!-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved. SPDX-License-Identifier: MIT-0 -->

# Welcome to your CDK Python project!

This is a blank project for Python development with CDK.

The `cdk.json` file tells the CDK Toolkit how to execute your app.

This project is set up like a standard Python project. The initialization process also creates a virtualenv within this
project, stored under the `.venv`
directory. To create the virtualenv it assumes that there is a `python3`
(or `python` for Windows) executable in your path with access to the `venv`
package. If for any reason the automatic creation of the virtualenv fails, you can create the virtualenv manually.

To manually create a virtualenv on MacOS and Linux:

```shell
$ python3 -m venv .venv
```

After the init process completes and the virtualenv is created, you can use the following step to activate your
virtualenv.

```shell
$ source .venv/bin/activate
```

If you are a Windows platform, you would activate the virtualenv like this:

```shell
% .venv\Scripts\activate.bat
```

Once the virtualenv is activated, you can install the required dependencies.

```shell
$ pip install -r requirements.txt
```

At this point you can now synthesize the CloudFormation template for this code.

```shell
$ cdk synth
```

To add additional dependencies, for example other CDK libraries, just add them to your `setup.py` file and rerun
the `pip install -r requirements.txt`
command.

## Useful commands

* `cdk ls`          list all stacks in the app
* `cdk synth`       emits the synthesized CloudFormation template
* `cdk deploy`      deploy this stack to your default AWS account/region
* `cdk diff`        compare deployed stack with current state
* `cdk destroy`     Destroy the stacks already created
* `cdk docs`        open CDK documentation

## FAQ
Q: When running `cdk deploy --all` I see the following error:  
```
Error [ValidationError]: Template format error: Unrecognized resource types: [AWS::Timestream::Table, AWS::Timestream::Database]
```

A: That means you are trying to deploy in a region where Timestream is currently not supported.

Enjoy!
