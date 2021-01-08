rem Copyright Amazon.com, Inc. and its affiliates. All Rights Reserved.
rem SPDX-License-Identifier: MIT-0
rem
rem Licensed under the MIT License. See the LICENSE accompanying this file
rem for the specific language governing permissions and limitations under
rem the License.

@echo off

rem The sole purpose of this script is to make the command
rem
rem     source .venv/bin/activate
rem
rem (which activates a Python virtualenv on Linux or Mac OS X) work on Windows.
rem On Windows, this command just runs this batch file (the argument is ignored).
rem
rem Now we don't need to document a Windows command for activating a virtualenv.

echo Executing .venv\Scripts\activate.bat for you
.venv\Scripts\activate.bat
