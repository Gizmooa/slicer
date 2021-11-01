from string_utils import StringUtils
from console import Console
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
type PACreatedEvent: void {
	type: string
	id: PAID
	info: ParkingAreaInformation
}
type PAUpdatedEvent: void {
	type: string
	id: PAID
	info: ParkingAreaInformation
}
type PADeletedEvent: void {
	type: string
	id: PAID
}
type DomainEvent: PACreatedEvent | DomainEvent: PAUpdatedEvent | DomainEvent: PADeletedEvent
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
type Topic: string
type Subscriber: void {
	topics: Topic
	location: string
}
interface EventStoreInterface {
	OneWay:
		publishEvent( DomainEvent )RequestResponse:
		subscribe( Subscriber )( SubscriptionResponse ),
		unsubscribe( Subscriber )( string )
}
service CommandSide ( any : config ){
	execution: concurrent
	inputPort InputCommands {
		location: config.CommandSide.location
		protocol: http{
			format = "json"
		}
		interfaces:
			CommandSideInterface,
			ShutDownInterface
	}
	outputPort EventStore {
		location: config.EventStore.location
		protocol: http{
			format = "json"
		}
		interfaces: EventStoreInterface
	}
	embed Console as C
	embed StringUtils as S
	main {
		[ createParkingArea( pa )( pa ){
			synchronized( dbToken ){
				id = #global.db
				global.db[id].id = id
				global.db[id].info << pa
			}
		} ]{
			valueToPrettyString@S( pa )( str )
			println@C( "UPDATED: " + str )(  )
			synchronized( dbToken ){
				event.type = "PA_CREATED"
				event[0] << globaldb[<Expression>]
				global.( "db" + 1 ) = 0
			}
			publishEvent@( event )
		}
		[ updateParkingArea( pa )( pa ){
			valueToPrettyString@S( pa )( str )
			println@C( "UPDATED: " + str )(  )
			synchronized( dbToken ){
				global.db[pa.id].info << pa.info
			}
			r = "OK"
		} ]{
			event.type = "PA_UPDATED"
			event << pa
			valueToPrettyString@S( pa )( str )
			println@C( "UPDATED: " + str )(  )
			publishEvent@( event )
		}
		[ deleteParkingArea( id )( id ){
			synchronized( dbToken ){
				undef( global.db[id] )
			}
			r = "OK"
		} ]{
			event << {
				type = "PA_DELETED"
				id = id
			}
			publishEvent@( event )
		}
		[ shutDown( void ) ]{
			println@C( "Shutting down" )(  )
			exit
		}
	}
}