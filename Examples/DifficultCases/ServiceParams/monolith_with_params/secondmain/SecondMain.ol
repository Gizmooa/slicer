from console import Console
type InitializeRequest : void {
	param1 : string
}
type InitializeReponse : void {
	rparam1 : string
}

type GreetRequest : void {
	name : string
}
type GreetResponse : void {
	greeting : string
}
interface GreeterAPI {
	RequestResponse:
		greet( GreetRequest )( GreetResponse ),
		initializeServiceParams( InitializeRequest )( InitializeReponse )
}
service SecondMain (){
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
		location: "socket://localhost:8080"
		protocol: http{
			format = "json"
		}
		interfaces: GreeterAPI
	}
	init {
		initializeServiceParams(request)(response){
			// POSSIBLY an error, I can't assign service params to request.
			// I need to define it as request.param1? TODO - Try multiple params inside request.
			global.serviceParams = request.param1 
			response.rparam1 = "From SecondMain: ServiceParams are updated!"
		}
		println@Console("#" + global.serviceParams)()
		greet@G( {name = global.serviceParams} )( response )
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
		println@Console(serviceParams)()
	}
	main {
		greet( request )( response ){
			println@Console( "SecondMain: forwarding '" + request.name + "' to G." )(  )
			
			greet@G( request )( response )
		}
	}
}