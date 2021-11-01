from math import Math
from string_utils import StringUtils
from console import Console
from time import Time
type PAID: long
type ParkingArea: void {
	id: PAID
	info: ParkingAreaInformation
}
type ParkingAreaInformation: void {
	name: string
	availability: TimePeriod
	chargingSpeed: ChargingSpeed
}
type ChargingSpeed: string
type TimePeriod: void {
	start: int
	end: int
}
interface CommandSideInterface {
	RequestResponse:
		updateParkingArea( ParkingArea )( string ),
		deleteParkingArea( PAID )( string ),
		createParkingArea( ParkingAreaInformation )( PAID )
}
interface ShutDownInterface {
	OneWay:
		shutDown( void )
}
type Location: int
interface QuerySideInterface {
	RequestResponse:
		getParkingArea( PAID )( GetParkingAreaResponse ),
		getParkingAreas( Location )( GetParkingAreasResponse )
}
service Tester {
	outputPort CS {
		location: "socket://localhost:10000"
		protocol: http{
			format = "json"
		}
		interfaces:
			CommandSideInterface,
			ShutDownInterface
	}
	outputPort QS {
		location: "socket://localhost:10001"
		protocol: http{
			format = "json"
		}
		interfaces:
			QuerySideInterface,
			ShutDownInterface
	}
	embed Math as M
	embed StringUtils as S
	embed Console as C
	embed Time as T
	main {
		chargingSpeeds[0] = "FAST"
		chargingSpeeds[1] = "SLOW"
		pushBack[0]->timePeriods[#timePeriods]
		pushBack << {
			start = 8
			end = 10
		}
		pushBack << {
			start = 10
			end = 12
		}
		pushBack << {
			start = 12
			end = 14
		}
		pushBack << {
			start = 14
			end = 16
		}
		for( i = 0, i < 17, i++ ){
			create_result[i] << {
				name = "PA" + i
				chargingSpeed = chargingSpeeds[<Expression>]
				availability << timePeriods[<Expression>]
			}
			valueToPrettyString@S( create_result[<Expression>] )( str )
			println@C( "Thread " + i + " " + str )(  )
			createParkingArea@CS( create_result[<Expression>] )( create_result[i] )
		}
		for( i = 0, i < 29, i++ ){
			getParkingArea@QS( create_result[<Expression>] )( parkingArea )
			parkingArea.info.chargingSpeed = chargingSpeeds[<Expression>]
			parkingArea.info.availability[#parkingArea.info.availability] << timePeriods[<Expression>]
			updateParkingArea@CS( parkingArea )(  )
		}
		spawn( i over 11 ) in location_result {
			random@M(  )( ratio )
			round@M( #create_result * ratio )( location )
			println@C( "Query Location: " + location )(  )
			getParkingAreas@QS( int( location ) )( location_result )
		}
		println@C( "Query Results:" )(  )
		for( res[0] in location_result ){
			valueToPrettyString@S( res )( str )
			println@C( str )(  )
		}
	}
}