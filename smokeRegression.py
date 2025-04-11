import subprocess

mvnVrsn = "3.5.4"
mvnPkg  = "apache-maven-{mvnVrsn}-bin.tar.gz".format(mvnVrsn=mvnVrsn)
mvnUrl  = "https://repository.walmart.com/content/repositories/central/org/apache/maven/apache-maven/{mvnVrsn}/{mvnPkg}".format(mvnVrsn=mvnVrsn, mvnPkg=mvnPkg)


def args_check(testCodeDir, testType):
    print(" *** Validating the passed arguments to the script *** ")
    if testCodeDir == "":
        raise Exception("testCodeDir = [ {testCodeDir} ] is not set".format(testCodeDir=testCodeDir))
    elif testType == "":
        raise Exception("testType = [ {testType} ] is not set".format(testType=testType))
    else:
    	print("testCodeDir = [ {testCodeDir} ], testType = [ {testType} ]".format(testCodeDir=testCodeDir, testType=testType))

def install_maven(mvnUrl, mvnPkg, mvnVrsn):
    print(" *** Installs maven and returns it's absolute path *** ")
    err_code = subprocess.Popen(["rm", "-rf",  "./apache-maven-{mvnVrsn}*"], stdout = subprocess.PIPE, shell=True)
    err_code.communicate()[0]
    if err_code.returncode != 0:
        print("Failed cleaning previsous maven installations")
    cmd = "wget {mvnUrl};tar -xzf {mvnPkg};./apache-maven-{mvnVrsn}/bin/mvn --version".format(mvnVrsn=mvnVrsn, mvnUrl=mvnUrl, mvnPkg=mvnPkg)
    cmdList = cmd.split(";")
    for cmd in cmdList:
        cmdList = cmd.split(" ")
        subprocess.check_output(cmdList)
    mvnUtility = "apache-maven-{mvnVrsn}/bin/mvn".format(mvnVrsn=mvnVrsn)
    print("Maven installation successful")
    return mvnUtility

def check_maven_install():
	version = subprocess.Popen(["mvn", "--version"], stdout = subprocess.PIPE, shell=True)
	version.communicate()[0]
	code = version.returncode
	print("Maven install return Code is: " + str(code))
	if code == 0:
		print(" Maven is present " )
		return True
	else:
		print(" Needs maven installation " )
		return False
		
def test_code_compile(mvnUtility, testCodeDir):
    print(" *** Compiles the test code *** ")
    cmd = "cd {testCodeDir} && ../{mvnUtility} clean test-compile -U -B -e -s settings.xml && cd ..".format(testCodeDir=testCodeDir, mvnUtility=mvnUtility)
    print("Compiling test code: " + cmd)
    err_code = subprocess.Popen([cmd], shell=True)
    err_code.communicate()[0]
    if err_code.returncode != 0:
        print("Failed compiling test code")
        raise Exception("Failed compiling test code")
    else:
    	print("Project compilation successful")
		
def test_code_run_publish_results(mvnUtility, testCodeDir, inputXml, environment, artifactId, version, testType):
    print(" *** Runs the smoke/ regression test and publish the results/ surefire report to Nexus *** ")
    # cmd = "cd {testCodeDir} && ../{mvnUtility} test deploy:deploy-file -DsuiteFile={inputXml} -Denv={environment} -Dgroup={groupId} -Dversion={version} -DArtifactId={artifactId} -DTestType={testType} -DCONTEXT_URL={contextUrl} -DbuildNumber={buildNumber} && cd ..".format(testCodeDir=testCodeDir, mvnUtility=mvnUtility, inputXml=inputXml) environment=environment, groupId=groupId, version=version, artifactId=artifactId, testType=testType, contextUrl=contextUrl, buildNumber=buildNumber)
    cmd = "cd {testCodeDir} && ../{mvnUtility} test -DsuiteFile={inputXml} -s settings.xml && cd ..".format(testCodeDir=testCodeDir, mvnUtility=mvnUtility, inputXml=inputXml)
    print("Running {testType} test and publish results ".format(testType = testType) +  cmd)
    err_code = subprocess.Popen(cmd, shell=True)
    err_code.communicate()[0]
    if err_code.returncode != 0:
        print("Failed compiling test code for: " + environment)
        raise Exception("{testType} execution failed for {environment}".format(testType=testType, environment=environment))
		
def main(testCodeDir, testType):
    print(" *** Main method to run user defined test ***")
    args_check(testCodeDir, testType)
    if check_maven_install() == False:
        mvnUtility = install_maven(mvnUrl, mvnPkg, mvnVrsn)
    else:
        mvnUtility = "apache-maven-{mvnVrsn}/bin/mvn".format(mvnVrsn=mvnVrsn)
    test_code_compile(mvnUtility, testCodeDir)
    inputXml = artifactId + "_" + testType + "Test.xml"
    test_code_run_publish_results(mvnUtility, testCodeDir, inputXml, environment, artifactId, version, testType)


if __name__ in [ "__main__", "__builtin__" ]:
    print("Inside smoke_regression.py, running: " + testType)
    main(testCodeDir, testType)
