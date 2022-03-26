from console import Console
type GreetRequest : void {
	name : string
}
type GreetResponse : void {
	greeting : string
}

type InitializeRequest : void {
	param1 : string
}
type InitializeReponse : void {
	rparam1 : string
}

interface MWAPI {
	RequestResponse:
		initializeServiceParams( InitializeRequest )( InitializeReponse )
}

interface GreeterAPI {
	RequestResponse:
		greet( GreetRequest )( GreetResponse )
}

service MW {
	execution: sequential
	embed Console as Console
	//embed SecondMain as SM - TODO - Remove after disembeding. If used, should be replaced by outputPort
	inputPort MWLocation {
		location: "socket://localhost:7979"
		protocol: http{
			format = "json"
		}
		interfaces: MWAPI
	}
	main {
		initializeServiceParams(request)(response){
			// POSSIBLY an error, I can't assign service params to request.
			// I need to define it as request.param1? TODO - Try multiple params inside request.
			global.serviceParams = request.param1 
			response.rparam1 = "From SecondMain: ServiceParams are updated!"
		}
	}
	embed SecondMain(global.serviceParams) as SM // TODO - This cant be done? :-(
}

service SecondMain (serviceParams : string) {
	execution: sequential
	embed Console as Console
	inputPort MainInput {
		location: "socket://localhost:8100"
		protocol: http{
			format = "json"
		}
		interfaces: GreeterAPI
	}
	outputPort G {
		location: "socket://localhost:8100"
		protocol: http{
			format = "json"
		}
		interfaces: GreeterAPI
	}
	init {
		println@Console( "SecondMain:
  G {
    location: '" + G.location + "'
    protocol: '" + G.protocol + "'
    interfaces: '" + G.interfaces + "'
  }" )(  )
		greet@G( {
			name = "SecondMain"
		} )( response )
		println@Console( "SecondMain: '" + response.greeting + "'" )(  )
	}
	main {
		greet( request )( response ){
			println@Console( "SecondMain: forwarding '" + request.name + "' to G." )(  )
			greet@G( request )( response )
		}
	}
}