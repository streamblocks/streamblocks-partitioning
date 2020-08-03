#include "actors-rts.h"
#include "natives.h"
#include "globals.h"

static void initNetwork(AbstractActorInstance ***pInstances, int *pNumberOfInstances, RuntimeOptions *options) {
	int numberOfInstances = 3;

	AbstractActorInstance **actorInstances = (AbstractActorInstance **) malloc(numberOfInstances * sizeof(AbstractActorInstance *));
	*pInstances = actorInstances;
	*pNumberOfInstances = numberOfInstances;

	// -- Instances declaration
	extern ActorClass ActorClass_system_plink_0;
	AbstractActorInstance *system_plink_0;
	InputPort *system_plink_0_source_Out_pass_In;
	OutputPort *system_plink_0_pass_Out_sink_In;

	extern ActorClass ActorClass_source;
	AbstractActorInstance *source;
	OutputPort *source_Out;

	extern ActorClass ActorClass_sink;
	AbstractActorInstance *sink;
	InputPort *sink_In;

	// -- Instances instantiation
	system_plink_0 = createActorInstance(&ActorClass_system_plink_0);
	system_plink_0->name = (char *) calloc(15, sizeof(char));
	strcpy(system_plink_0->name, "system_plink_0");
	system_plink_0_source_Out_pass_In = createInputPort(system_plink_0, "source_Out_pass_In", BUFFER_SIZE_WORDS);
	system_plink_0_pass_Out_sink_In = createOutputPort(system_plink_0, "pass_Out_sink_In", 1);
	actorInstances[0] = system_plink_0;

	source = createActorInstance(&ActorClass_source);
	source->name = (char *) calloc(7, sizeof(char));
	strcpy(source->name, "source");
	source_Out = createOutputPort(source, "Out", 1);
	actorInstances[1] = source;

	sink = createActorInstance(&ActorClass_sink);
	sink->name = (char *) calloc(5, sizeof(char));
	strcpy(sink->name, "sink");
	sink_In = createInputPort(sink, "In", BUFFER_SIZE_WORDS);
	actorInstances[2] = sink;

	// -- Connections
	connectPorts(system_plink_0_pass_Out_sink_In, sink_In);
	connectPorts(source_Out, system_plink_0_source_Out_pass_In);

	// -- Initialize Global Variables
	init_global_variables();
}

int main(int argc, char *argv[]) {
	RuntimeOptions *options = (RuntimeOptions *) calloc(1, sizeof(RuntimeOptions));
	int numberOfInstances;

	pre_parse_args(argc, argv, options);
	AbstractActorInstance **instances;
	initNetwork(&instances, &numberOfInstances, options);
	return executeNetwork(argc, argv, options, instances, numberOfInstances);
}
