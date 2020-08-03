`include "TriggerTypes.sv"
import TriggerTypes::*;

module source_Out_pass_In_input_stage #(
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH = 64,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH = 32,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_AWUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_ARUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_WUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_RUSER_WIDTH = 1,
	parameter integer C_M_AXI_SOURCE_OUT_PASS_IN_BUSER_WIDTH =  1
)
(
	input wire ap_clk,
	input wire ap_rst_n,
	// -- ap control
	input  wire ap_start,
	output wire ap_idle,
	output wire ap_ready,
	output wire ap_done,
	output wire [31:0] ap_return,
	// -- write address channel
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_AWID,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH-1:0]   m_axi_source_Out_pass_In_AWADDR,
	output wire [7:0]                         m_axi_source_Out_pass_In_AWLEN,
	output wire [2:0]                         m_axi_source_Out_pass_In_AWSIZE,
	output wire [1:0]                         m_axi_source_Out_pass_In_AWBURST,
	output wire [1:0]                         m_axi_source_Out_pass_In_AWLOCK,
	output wire [3:0]                         m_axi_source_Out_pass_In_AWCACHE,
	output wire [2:0]                         m_axi_source_Out_pass_In_AWPROT,
	output wire [3:0]                         m_axi_source_Out_pass_In_AWQOS,
	output wire [3:0]                         m_axi_source_Out_pass_In_AWREGION,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_AWUSER_WIDTH-1:0] m_axi_source_Out_pass_In_AWUSER,
	output wire                               m_axi_source_Out_pass_In_AWVALID,
	input  wire                               m_axi_source_Out_pass_In_AWREADY,
	// -- write data channel
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_WID,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH-1:0]   m_axi_source_Out_pass_In_WDATA,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH/8-1:0] m_axi_source_Out_pass_In_WSTRB,
	output wire                               m_axi_source_Out_pass_In_WLAST,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_WUSER_WIDTH-1:0]  m_axi_source_Out_pass_In_WUSER,
	output wire                               m_axi_source_Out_pass_In_WVALID,
	input  wire                               m_axi_source_Out_pass_In_WREADY,
	// -- write response channel
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_BID,
	input  wire [1:0]                         m_axi_source_Out_pass_In_BRESP,
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_BUSER_WIDTH-1:0]  m_axi_source_Out_pass_In_BUSER,
	input  wire                               m_axi_source_Out_pass_In_BVALID,
	output wire                               m_axi_source_Out_pass_In_BREADY,
	// -- read address channel
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_ARID,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH-1:0]   m_axi_source_Out_pass_In_ARADDR,
	output wire [7:0]                         m_axi_source_Out_pass_In_ARLEN,
	output wire [2:0]                         m_axi_source_Out_pass_In_ARSIZE,
	output wire [1:0]                         m_axi_source_Out_pass_In_ARBURST,
	output wire [1:0]                         m_axi_source_Out_pass_In_ARLOCK,
	output wire [3:0]                         m_axi_source_Out_pass_In_ARCACHE,
	output wire [2:0]                         m_axi_source_Out_pass_In_ARPROT,
	output wire [3:0]                         m_axi_source_Out_pass_In_ARQOS,
	output wire [3:0]                         m_axi_source_Out_pass_In_ARREGION,
	output wire [C_M_AXI_SOURCE_OUT_PASS_IN_ARUSER_WIDTH-1:0] m_axi_source_Out_pass_In_ARUSER,
	output wire                               m_axi_source_Out_pass_In_ARVALID,
	input  wire                               m_axi_source_Out_pass_In_ARREADY,
	// -- read data channel
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH-1:0]     m_axi_source_Out_pass_In_RID,
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH-1:0]   m_axi_source_Out_pass_In_RDATA,
	input  wire [1:0]                         m_axi_source_Out_pass_In_RRESP,
	input  wire                               m_axi_source_Out_pass_In_RLAST,
	input  wire [C_M_AXI_SOURCE_OUT_PASS_IN_RUSER_WIDTH-1:0]  m_axi_source_Out_pass_In_RUSER,
	input  wire                               m_axi_source_Out_pass_In_RVALID,
	output wire                               m_axi_source_Out_pass_In_RREADY,
	// -- Constant & Addresses
	input  wire [31:0] source_Out_pass_In_requested_size,
	input  wire [63:0] source_Out_pass_In_size_r,
	input  wire [63:0] source_Out_pass_In_buffer,
	input  wire [63:0] source_Out_pass_In_offset,
	input  wire [31:0] kernel_command,
	// --- Trigger signals
	input  wire all_sync,
	input  wire all_sync_wait,
	input  wire all_sleep,
	input  wire all_waited,
	output wire source_Out_pass_In_sleep,
	output wire source_Out_pass_In_sync_wait,
	output wire source_Out_pass_In_sync_exec,
	output wire source_Out_pass_In_waited,
	// -- output stream
	output  wire [31:0] source_Out_pass_In_din,
	input   wire source_Out_pass_In_full_n,
	output  wire source_Out_pass_In_write, 
	input   wire [31:0] source_Out_pass_In_fifo_count,
	input   wire [31:0] source_Out_pass_In_fifo_size
);
	timeunit 1ps;
	timeprecision 1ps;
	// -- --------------------------------------------------------------------------
	// -- Reg & Wires
	// -- --------------------------------------------------------------------------

	// -- fifo_count register
	logic    [31:0] source_Out_pass_In_fifo_count_reg = 32'd0;

	// -- Input stage mem
	// -- Trigger wires for port: source_Out_pass_In
	wire    source_Out_pass_In_input_stage_ap_start;
	wire    source_Out_pass_In_input_stage_ap_done;
	wire    source_Out_pass_In_input_stage_ap_ready;
	wire    source_Out_pass_In_input_stage_ap_idle;
	wire    [31 : 0] source_Out_pass_In_input_stage_ap_return;
	wire    source_Out_pass_In_input_stage_launch_predicate;
	wire    source_Out_pass_In_at_least_half_empty;
	localparam mode_t trigger_mode = ACTOR_TRIGGER;
	logic    [32 - 1:0] source_Out_pass_In_sleep_counter = 32'd0;
	wire     stage_idle;

	// -- offset fifo wires
	wire source_Out_pass_In_offset_empty_n;
	wire source_Out_pass_In_offset_full_n;
	wire [63 : 0] source_Out_pass_In_offset_dout;
	wire [63 : 0] source_Out_pass_In_offset_din;
	wire source_Out_pass_In_offset_read;
	wire source_Out_pass_In_offset_write;

	// -- FIFO count sampling
	always_ff @(posedge ap_clk) begin
		if (ap_rst_n == 1'b0)
			source_Out_pass_In_fifo_count_reg <= 0;
		else if(source_Out_pass_In_input_stage_ap_idle == 1'b1 || source_Out_pass_In_input_stage_ap_done == 1'b1)
			source_Out_pass_In_fifo_count_reg <= source_Out_pass_In_fifo_count;
	end

	// -- --------------------------------------------------------------------------
	// -- Instantiations
	// -- --------------------------------------------------------------------------

	// --- offset FIFO
	FIFO #(
		.MEM_STYLE("auto"),
		.DATA_WIDTH(64),
		.ADDR_WIDTH(1)
	) source_Out_pass_In_offset_fifo(
		.clk(ap_clk),
		.reset_n(ap_rst_n),
		.if_full_n(source_Out_pass_In_offset_full_n),
		.if_write(source_Out_pass_In_offset_write),
		.if_din(source_Out_pass_In_offset_din),

		.if_empty_n(source_Out_pass_In_offset_empty_n),
		.if_read(source_Out_pass_In_offset_read),
		.if_dout(source_Out_pass_In_offset_dout),

		.peek(),
		.count(),
		.size() 
	);

	// -- Trigger control for port : source_Out_pass_In

	trigger #(.mode(trigger_mode)) source_Out_pass_In_trigger (
		.ap_clk(ap_clk),
		.ap_rst_n(ap_rst_n),
		.ap_start(ap_start),
		.ap_done(ap_done),
		.ap_idle(stage_idle),
		.ap_ready(ap_ready),
		.external_enqueue(1'b0),
		.all_sync(all_sync),
		.all_sync_wait(all_sync_wait),
		.all_sleep(all_sleep),
		.sleep(source_Out_pass_In_sleep),
		.sync_exec(source_Out_pass_In_sync_exec),
		.sync_wait(source_Out_pass_In_sync_wait),
		.all_waited(all_waited),
		.waited(source_Out_pass_In_waited),
		.actor_return(source_Out_pass_In_input_stage_ap_return[1:0]),
		.actor_done(source_Out_pass_In_input_stage_ap_done),
		.actor_ready(source_Out_pass_In_input_stage_ap_ready),
		.actor_idle(source_Out_pass_In_input_stage_ap_idle),
		.actor_start(source_Out_pass_In_input_stage_ap_start)
	);

	// -- sleep timer
	always_ff @(posedge ap_clk) begin
		if (ap_rst_n == 1'b0 | source_Out_pass_In_sleep == 1'b0)
			source_Out_pass_In_sleep_counter <= 0;
		else if (source_Out_pass_In_sleep == 1'b1)
			source_Out_pass_In_sleep_counter <= source_Out_pass_In_sleep_counter + 32'd1;
	end
	// -- Input stage mem for port : source_Out_pass_In

	source_Out_pass_In_input_stage_mem #(
		.C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_ID_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_ADDR_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_DATA_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_AWUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_AWUSER_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_ARUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_ARUSER_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_WUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_WUSER_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_RUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_RUSER_WIDTH ),
		.C_M_AXI_SOURCE_OUT_PASS_IN_BUSER_WIDTH( C_M_AXI_SOURCE_OUT_PASS_IN_BUSER_WIDTH )
	)
	i_source_Out_pass_In_input_stage_mem(
		.ap_clk(ap_clk),
		.ap_rst_n(ap_rst_n),
		.ap_start(source_Out_pass_In_input_stage_ap_start),
		.ap_done(source_Out_pass_In_input_stage_ap_done),
		.ap_idle(source_Out_pass_In_input_stage_ap_idle),
		.ap_ready(source_Out_pass_In_input_stage_ap_ready),
		.ap_return(source_Out_pass_In_input_stage_ap_return),
		.m_axi_source_Out_pass_In_AWVALID(m_axi_source_Out_pass_In_AWVALID),
		.m_axi_source_Out_pass_In_AWREADY(m_axi_source_Out_pass_In_AWREADY),
		.m_axi_source_Out_pass_In_AWADDR(m_axi_source_Out_pass_In_AWADDR),
		.m_axi_source_Out_pass_In_AWID(m_axi_source_Out_pass_In_AWID),
		.m_axi_source_Out_pass_In_AWLEN(m_axi_source_Out_pass_In_AWLEN),
		.m_axi_source_Out_pass_In_AWSIZE(m_axi_source_Out_pass_In_AWSIZE),
		.m_axi_source_Out_pass_In_AWBURST(m_axi_source_Out_pass_In_AWBURST),
		.m_axi_source_Out_pass_In_AWLOCK(m_axi_source_Out_pass_In_AWLOCK),
		.m_axi_source_Out_pass_In_AWCACHE(m_axi_source_Out_pass_In_AWCACHE),
		.m_axi_source_Out_pass_In_AWPROT(m_axi_source_Out_pass_In_AWPROT),
		.m_axi_source_Out_pass_In_AWQOS(m_axi_source_Out_pass_In_AWQOS),
		.m_axi_source_Out_pass_In_AWREGION(m_axi_source_Out_pass_In_AWREGION),
		.m_axi_source_Out_pass_In_AWUSER(m_axi_source_Out_pass_In_AWUSER),
		.m_axi_source_Out_pass_In_WVALID(m_axi_source_Out_pass_In_WVALID),
		.m_axi_source_Out_pass_In_WREADY(m_axi_source_Out_pass_In_WREADY),
		.m_axi_source_Out_pass_In_WDATA(m_axi_source_Out_pass_In_WDATA),
		.m_axi_source_Out_pass_In_WSTRB(m_axi_source_Out_pass_In_WSTRB),
		.m_axi_source_Out_pass_In_WLAST(m_axi_source_Out_pass_In_WLAST),
		.m_axi_source_Out_pass_In_WID(m_axi_source_Out_pass_In_WID),
		.m_axi_source_Out_pass_In_WUSER(m_axi_source_Out_pass_In_WUSER),
		.m_axi_source_Out_pass_In_ARVALID(m_axi_source_Out_pass_In_ARVALID),
		.m_axi_source_Out_pass_In_ARREADY(m_axi_source_Out_pass_In_ARREADY),
		.m_axi_source_Out_pass_In_ARADDR(m_axi_source_Out_pass_In_ARADDR),
		.m_axi_source_Out_pass_In_ARID(m_axi_source_Out_pass_In_ARID),
		.m_axi_source_Out_pass_In_ARLEN(m_axi_source_Out_pass_In_ARLEN),
		.m_axi_source_Out_pass_In_ARSIZE(m_axi_source_Out_pass_In_ARSIZE),
		.m_axi_source_Out_pass_In_ARBURST(m_axi_source_Out_pass_In_ARBURST),
		.m_axi_source_Out_pass_In_ARLOCK(m_axi_source_Out_pass_In_ARLOCK),
		.m_axi_source_Out_pass_In_ARCACHE(m_axi_source_Out_pass_In_ARCACHE),
		.m_axi_source_Out_pass_In_ARPROT(m_axi_source_Out_pass_In_ARPROT),
		.m_axi_source_Out_pass_In_ARQOS(m_axi_source_Out_pass_In_ARQOS),
		.m_axi_source_Out_pass_In_ARREGION(m_axi_source_Out_pass_In_ARREGION),
		.m_axi_source_Out_pass_In_ARUSER(m_axi_source_Out_pass_In_ARUSER),
		.m_axi_source_Out_pass_In_RVALID(m_axi_source_Out_pass_In_RVALID),
		.m_axi_source_Out_pass_In_RREADY(m_axi_source_Out_pass_In_RREADY),
		.m_axi_source_Out_pass_In_RDATA(m_axi_source_Out_pass_In_RDATA),
		.m_axi_source_Out_pass_In_RLAST(m_axi_source_Out_pass_In_RLAST),
		.m_axi_source_Out_pass_In_RID(m_axi_source_Out_pass_In_RID),
		.m_axi_source_Out_pass_In_RUSER(m_axi_source_Out_pass_In_RUSER),
		.m_axi_source_Out_pass_In_RRESP(m_axi_source_Out_pass_In_RRESP),
		.m_axi_source_Out_pass_In_BVALID(m_axi_source_Out_pass_In_BVALID),
		.m_axi_source_Out_pass_In_BREADY(m_axi_source_Out_pass_In_BREADY),
		.m_axi_source_Out_pass_In_BRESP(m_axi_source_Out_pass_In_BRESP),
		.m_axi_source_Out_pass_In_BID(m_axi_source_Out_pass_In_BID),
		.m_axi_source_Out_pass_In_BUSER(m_axi_source_Out_pass_In_BUSER),

		.source_Out_pass_In_requested_size(source_Out_pass_In_requested_size),
		.source_Out_pass_In_size_V(source_Out_pass_In_size_r),
		.source_Out_pass_In_buffer_V(source_Out_pass_In_buffer),
		.fifo_count(source_Out_pass_In_fifo_count_reg),
		.fifo_size(source_Out_pass_In_fifo_size),
		.source_Out_pass_In_V_din(source_Out_pass_In_din),
		.source_Out_pass_In_V_full_n(source_Out_pass_In_full_n),
		.source_Out_pass_In_V_write(source_Out_pass_In_write),
		.source_Out_pass_In_offset_V_dout(source_Out_pass_In_offset_dout),
		.source_Out_pass_In_offset_V_empty_n(source_Out_pass_In_offset_empty_n),
		.source_Out_pass_In_offset_V_read(source_Out_pass_In_offset_read)
	);

	// -- Offset fifo (initialization) logic
	assign source_Out_pass_In_offset_write = ap_start;
	assign source_Out_pass_In_offset_din = source_Out_pass_In_offset;
	assign  ap_idle = stage_idle;
	endmodule
