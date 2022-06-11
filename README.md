# Jolie Slicer
## About the slicer
We propose Sliceable Monolith, a new methodology for developing microservice architectures and perform their integration testing by leveraging most of the simplicity of a monolith: a single codebase and a local execution environment that simulates distribution. Then, a tool compiles a codebase for each microservice and a cloud deployment configuration. The key enabler of our approach is the technology-agnostic service definition language offered by Jolie.

## Download and setup the slicer
The following steps require Jolie and Java 11 to be installed, there will be provided a step-by-step guide, in the end, describing how to set the Jolie development version up. 

1) Clone Jolie Slicer GitHub repository at: https://github.com/Gizmooa/slicer
2) Change directory to the slicer, and download maven dependencies using the command “mvn install”
3) Create the following symlinks to use the slicer in any location:
```
sudo ln -s /path/to/launcher.ol /usr/local/bin/slicer
sudo ln -s /path/to/slicer/dist /path/to/slicer/lib
```
4) Might get errors because of access permissions, be sure to change them for "launcher.ol"
```
chmod +x launcher.ol
```
5) Success! The slicer should now be callable in any location on your system. Try calling "slicer" and it should print the usage information. 

## How to use the slicer
The slicer takes one required parameter; a Jolie file ("monolith.ol") and three optional parameters where at least one of them needs to be present. Usage will be shown beneath where optional parameters are within square brackets, and required parameters are within angle brackets.
```
slicer [--config config.json] [--disembedConfig disembed.json] [--visualize example.dot] <monolith.ol>
```

1) --config config.json

The config flag needs to be provided with a json file that defines what services the user wants to extract from the monolith, which in the example beneath are Foo and Bar. This will generate two directories; foo and bar containing docker deployment files, jolie file containing the service, and the config.json. (The config.json can be used for e.g., service parameters in this case) Along with the two directories a docker-compose.yaml file will be generated to spin up the services defined in the config.
```
{
    "Foo": {
        "location" : "local://5000"
    },
    "Bar": {
        "location" : "local://5001"
    }
}
```

2) --disembedConfig disembed.json

The config flag does not make the program docker-ready and therefore the application already needs to be docker ready before slicing. If the user have a monolith they want to make docker-ready, they can use the disembed flag, also provided with a json file. An example for such a json file will be shown beneath where the user wants to disembed Bar from Foo, making Bar a stand-alone service. The program will then generate a config.json with the stand-alone services as keys. If there already exists a config.json with the service as a key, the program will use those values instead of a dummy value. This way the user can provide a config.json together with a disembed.json to provide service parameters and dependencies. 
```
{
    "Foo": [
      "Bar"
    ]
}
```

3) --visualize example.dot

The visualization flag needs to be provided with a name of a dot file e.g., example.dot. Then combined with either one of the flags above, the program will generate a dot graph corresponding to the system the slicer will generated after slicing with the provided flags and monolith.

## Setting up jolie development version
1) Clone jolie GitHub repository at: https://github.com/jolie/jolie
2) Change directory to jolie/ and download maven dependencies using the command “mvn install”
3) Download dev-setup for jolie by running the command: “./scripts/dev-setup.sh $YOUR_PATH”, where $YOUR_PATH e.g. could be /usr/local/bin
4) Add "JOLIE_HOME=”/$YOUR_PATH/jolie-dist" to .bashrc
5) Log in and out, should now be able to use "jolie --version" to see the current version
