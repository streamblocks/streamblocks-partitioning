#include "actors-rts.h"

#define STR_HELPER(x) #x
#define STR(x) STR_HELPER(x)

#define BUFFER_SIZE_WORDS (BUFFER_SIZE / 4)
// #define BUFFERSIZE	4096


static void initNetwork(AbstractActorInstance ***pInstances, int *pNumberOfInstances) {
	int numberOfInstances = 2;

	AbstractActorInstance **actorInstances = (AbstractActorInstance **) malloc(numberOfInstances * sizeof(AbstractActorInstance *));
	*pInstances = actorInstances;
	*pNumberOfInstances = numberOfInstances;

	// -- Instances declaration
	extern ActorClass ActorClass_TimedSource;
	
	AbstractActorInstance *source;
	OutputPort *source_Out;

	extern ActorClass ActorClass_TimedSink;
	AbstractActorInstance *sink;
	InputPort *sink_In;


	

	// -- Instances instantiation
	source = createActorInstance(&ActorClass_TimedSource);
	setParameter(source, "repeat", STR(NUM_LOOPS));
	source_Out = createOutputPort(source, "Out", 1);
	actorInstances[0] = source;

	sink = createActorInstance(&ActorClass_TimedSink);
	sink_In = createInputPort(sink, "In", BUFFER_SIZE_WORDS);
	actorInstances[1] = sink;

	

	// -- Connections
	connectPorts(source_Out, sink_In);
	
	// -- Initialize Global Variables
	// init_global_variables();
}

int main(int argc, char *argv[]) {
	int numberOfInstances;
	AbstractActorInstance **instances;
	initNetwork(&instances, &numberOfInstances);
	return executeNetwork(argc, argv, instances, numberOfInstances);
}
