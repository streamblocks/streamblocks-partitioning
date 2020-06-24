#include "actors-rts.h"

#define STR_HELPER(x) #x
#define STR(x) STR_HELPER(x)

#define BUFFER_SIZE_WORDS (BUFFER_SIZE / 4)



static void initNetwork(AbstractActorInstance ***pInstances, int *pNumberOfInstances) {
	int numberOfInstances = 2;

	AbstractActorInstance **actorInstances = (AbstractActorInstance **) malloc(numberOfInstances * sizeof(AbstractActorInstance *));
	*pInstances = actorInstances;
	*pNumberOfInstances = numberOfInstances;

	// -- Instances declaration
	extern ActorClass ActorClass_TimedSource;
	AbstractActorInstance *source;
	OutputPort *source_Out;
	InputPort *source_In;

	extern ActorClass ActorClass_PassThrough;
	AbstractActorInstance *pass;
	InputPort *pass_In;
	OutputPort *pass_Out;


	

	// -- Instances instantiation
	source = createActorInstance(&ActorClass_TimedSource);
	source_In = createInputPort(source, "In", BUFFER_SIZE_WORDS);
	source_Out = createOutputPort(source, "Out", 1);
	actorInstances[0] = source;

  pass = createActorInstance(&ActorClass_PassThrough);
	pass_In = createInputPort(pass, "In", BUFFER_SIZE_WORDS);
	pass_Out = createOutputPort(pass, "Out", 1);

	actorInstances[1] = pass;


	// -- Connections
	connectPorts(source_Out, pass_In);
	connectPorts(pass_Out, source_In);
	
	// -- Initialize Global Variables
	// init_global_variables();
}

int main(int argc, char *argv[]) {
	int numberOfInstances;
	AbstractActorInstance **instances;
	initNetwork(&instances, &numberOfInstances);
	return executeNetwork(argc, argv, instances, numberOfInstances);
}
