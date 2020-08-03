`include "TriggerTypes.sv"
import TriggerTypes::*;

module pass_Out_sink_In_output_stage #(
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH = 64,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH = 32,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_AWUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_ARUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_WUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_RUSER_WIDTH = 1,
	parameter integer C_M_AXI_PASS_OUT_SINK_IN_BUSER_WIDTH =  1
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
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_AWID,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH-1:0]   m_axi_pass_Out_sink_In_AWADDR,
	output wire [7:0]                         m_axi_pass_Out_sink_In_AWLEN,
	output wire [2:0]                         m_axi_pass_Out_sink_In_AWSIZE,
	output wire [1:0]                         m_axi_pass_Out_sink_In_AWBURST,
	output wire [1:0]                         m_axi_pass_Out_sink_In_AWLOCK,
	output wire [3:0]                         m_axi_pass_Out_sink_In_AWCACHE,
	output wire [2:0]                         m_axi_pass_Out_sink_In_AWPROT,
	output wire [3:0]                         m_axi_pass_Out_sink_In_AWQOS,
	output wire [3:0]                         m_axi_pass_Out_sink_In_AWREGION,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_AWUSER_WIDTH-1:0] m_axi_pass_Out_sink_In_AWUSER,
	output wire                               m_axi_pass_Out_sink_In_AWVALID,
	input  wire                               m_axi_pass_Out_sink_In_AWREADY,
	// -- write data channel
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_WID,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH-1:0]   m_axi_pass_Out_sink_In_WDATA,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH/8-1:0] m_axi_pass_Out_sink_In_WSTRB,
	output wire                               m_axi_pass_Out_sink_In_WLAST,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_WUSER_WIDTH-1:0]  m_axi_pass_Out_sink_In_WUSER,
	output wire                               m_axi_pass_Out_sink_In_WVALID,
	input  wire                               m_axi_pass_Out_sink_In_WREADY,
	// -- write response channel
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_BID,
	input  wire [1:0]                         m_axi_pass_Out_sink_In_BRESP,
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_BUSER_WIDTH-1:0]  m_axi_pass_Out_sink_In_BUSER,
	input  wire                               m_axi_pass_Out_sink_In_BVALID,
	output wire                               m_axi_pass_Out_sink_In_BREADY,
	// -- read address channel
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_ARID,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH-1:0]   m_axi_pass_Out_sink_In_ARADDR,
	output wire [7:0]                         m_axi_pass_Out_sink_In_ARLEN,
	output wire [2:0]                         m_axi_pass_Out_sink_In_ARSIZE,
	output wire [1:0]                         m_axi_pass_Out_sink_In_ARBURST,
	output wire [1:0]                         m_axi_pass_Out_sink_In_ARLOCK,
	output wire [3:0]                         m_axi_pass_Out_sink_In_ARCACHE,
	output wire [2:0]                         m_axi_pass_Out_sink_In_ARPROT,
	output wire [3:0]                         m_axi_pass_Out_sink_In_ARQOS,
	output wire [3:0]                         m_axi_pass_Out_sink_In_ARREGION,
	output wire [C_M_AXI_PASS_OUT_SINK_IN_ARUSER_WIDTH-1:0] m_axi_pass_Out_sink_In_ARUSER,
	output wire                               m_axi_pass_Out_sink_In_ARVALID,
	input  wire                               m_axi_pass_Out_sink_In_ARREADY,
	// -- read data channel
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH-1:0]     m_axi_pass_Out_sink_In_RID,
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH-1:0]   m_axi_pass_Out_sink_In_RDATA,
	input  wire [1:0]                         m_axi_pass_Out_sink_In_RRESP,
	input  wire                               m_axi_pass_Out_sink_In_RLAST,
	input  wire [C_M_AXI_PASS_OUT_SINK_IN_RUSER_WIDTH-1:0]  m_axi_pass_Out_sink_In_RUSER,
	input  wire                               m_axi_pass_Out_sink_In_RVALID,
	output wire                               m_axi_pass_Out_sink_In_RREADY,
	// -- Constant & Addresses
	input  wire [31:0] pass_Out_sink_In_available_size,
	input  wire [63:0] pass_Out_sink_In_size_r,
	input  wire [63:0] pass_Out_sink_In_buffer,
	input  wire [63:0] pass_Out_sink_In_offset,
	input  wire [31:0] kernel_command,
	// --- Trigger signals
	input  wire all_sync,
	input  wire all_sync_wait,
	input  wire all_sleep,
	input  wire all_waited,
	output wire pass_Out_sink_In_sleep,
	output wire pass_Out_sink_In_sync_wait,
	output wire pass_Out_sink_In_sync_exec,
	output wire pass_Out_sink_In_waited,
	// -- output stream
	input    wire [31:0] pass_Out_sink_In_dout,
	input    wire pass_Out_sink_In_empty_n,
	output   wire pass_Out_sink_In_read, 
	input    wire [31:0] pass_Out_sink_In_fifo_count, 
	input    wire [31:0] pass_Out_sink_In_fifo_size 
);
timeunit 1ps;
timeprecision 1ps;
// -- --------------------------------------------------------------------------
// -- Reg & Wires
// -- --------------------------------------------------------------------------

// -- Queue wires
logic    [31:0] pass_Out_sink_In_fifo_count_reg = 32'd0;
// -- Output stage mem
wire   pass_Out_sink_In_output_stage_ap_start;
wire   pass_Out_sink_In_output_stage_ap_done;
wire   pass_Out_sink_In_output_stage_ap_idle;
wire   pass_Out_sink_In_output_stage_ap_ready;
wire   [31 : 0] pass_Out_sink_In_output_stage_ap_return;
localparam mode_t trigger_mode = ACTOR_TRIGGER;

// -- offset fifo wires
wire pass_Out_sink_In_offset_empty_n;
wire pass_Out_sink_In_offset_full_n;
wire [63 : 0] pass_Out_sink_In_offset_dout;
wire [63 : 0] pass_Out_sink_In_offset_din;
wire pass_Out_sink_In_offset_read;
wire pass_Out_sink_In_offset_write;

// -- FIFO count sampling
always_ff @(posedge ap_clk) begin
	if (ap_rst_n == 1'b0)
		pass_Out_sink_In_fifo_count_reg <= 0;
	else if(pass_Out_sink_In_output_stage_ap_idle == 1'b1 || pass_Out_sink_In_output_stage_ap_done == 1'b1)
		pass_Out_sink_In_fifo_count_reg <= pass_Out_sink_In_fifo_count;
end

// -- --------------------------------------------------------------------------
// -- Instantiations
// -- --------------------------------------------------------------------------

// -- Trigger control for port : pass_Out_sink_In

trigger #(.mode(trigger_mode)) pass_Out_sink_In_trigger (
	.ap_clk(ap_clk),
	.ap_rst_n(ap_rst_n),
	.ap_start(ap_start),
	.ap_done(ap_done),
	.ap_idle(ap_idle),
	.ap_ready(ap_ready),
	.external_enqueue(1'b0),
	.all_sync(all_sync),
	.all_sync_wait(all_sync_wait),
	.all_sleep(all_sleep),
	.all_waited(all_waited),
	.sync_exec(pass_Out_sink_In_sync_exec),
	.sync_wait(pass_Out_sink_In_sync_wait),
	.waited(pass_Out_sink_In_waited),
	.sleep(pass_Out_sink_In_sleep),
	.actor_return(pass_Out_sink_In_output_stage_ap_return[1:0]),
	.actor_done(pass_Out_sink_In_output_stage_ap_done),
	.actor_ready(pass_Out_sink_In_output_stage_ap_ready),
	.actor_idle(pass_Out_sink_In_output_stage_ap_idle),
	.actor_start(pass_Out_sink_In_output_stage_ap_start)
);

// -- Output stage mem for port : pass_Out_sink_In
pass_Out_sink_In_output_stage_mem #(
	.C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_ID_WIDTH ),
	.C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_ADDR_WIDTH ),
	.C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_DATA_WIDTH ),
	.C_M_AXI_PASS_OUT_SINK_IN_AWUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_AWUSER_WIDTH ),
	.C_M_AXI_PASS_OUT_SINK_IN_ARUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_ARUSER_WIDTH ),
	.C_M_AXI_PASS_OUT_SINK_IN_WUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_WUSER_WIDTH ),
	.C_M_AXI_PASS_OUT_SINK_IN_RUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_RUSER_WIDTH ),
	.C_M_AXI_PASS_OUT_SINK_IN_BUSER_WIDTH( C_M_AXI_PASS_OUT_SINK_IN_BUSER_WIDTH )
)
i_pass_Out_sink_In_output_stage_mem(
	.ap_clk(ap_clk),
	.ap_rst_n(ap_rst_n),
	.ap_start(pass_Out_sink_In_output_stage_ap_start),
	.ap_done(pass_Out_sink_In_output_stage_ap_done),
	.ap_idle(pass_Out_sink_In_output_stage_ap_idle),
	.ap_ready(pass_Out_sink_In_output_stage_ap_ready),
	.ap_return(pass_Out_sink_In_output_stage_ap_return),
	.m_axi_pass_Out_sink_In_AWVALID(m_axi_pass_Out_sink_In_AWVALID),
	.m_axi_pass_Out_sink_In_AWREADY(m_axi_pass_Out_sink_In_AWREADY),
	.m_axi_pass_Out_sink_In_AWADDR(m_axi_pass_Out_sink_In_AWADDR),
	.m_axi_pass_Out_sink_In_AWID(m_axi_pass_Out_sink_In_AWID),
	.m_axi_pass_Out_sink_In_AWLEN(m_axi_pass_Out_sink_In_AWLEN),
	.m_axi_pass_Out_sink_In_AWSIZE(m_axi_pass_Out_sink_In_AWSIZE),
	.m_axi_pass_Out_sink_In_AWBURST(m_axi_pass_Out_sink_In_AWBURST),
	.m_axi_pass_Out_sink_In_AWLOCK(m_axi_pass_Out_sink_In_AWLOCK),
	.m_axi_pass_Out_sink_In_AWCACHE(m_axi_pass_Out_sink_In_AWCACHE),
	.m_axi_pass_Out_sink_In_AWPROT(m_axi_pass_Out_sink_In_AWPROT),
	.m_axi_pass_Out_sink_In_AWQOS(m_axi_pass_Out_sink_In_AWQOS),
	.m_axi_pass_Out_sink_In_AWREGION(m_axi_pass_Out_sink_In_AWREGION),
	.m_axi_pass_Out_sink_In_AWUSER(m_axi_pass_Out_sink_In_AWUSER),
	.m_axi_pass_Out_sink_In_WVALID(m_axi_pass_Out_sink_In_WVALID),
	.m_axi_pass_Out_sink_In_WREADY(m_axi_pass_Out_sink_In_WREADY),
	.m_axi_pass_Out_sink_In_WDATA(m_axi_pass_Out_sink_In_WDATA),
	.m_axi_pass_Out_sink_In_WSTRB(m_axi_pass_Out_sink_In_WSTRB),
	.m_axi_pass_Out_sink_In_WLAST(m_axi_pass_Out_sink_In_WLAST),
	.m_axi_pass_Out_sink_In_WID(m_axi_pass_Out_sink_In_WID),
	.m_axi_pass_Out_sink_In_WUSER(m_axi_pass_Out_sink_In_WUSER),
	.m_axi_pass_Out_sink_In_ARVALID(m_axi_pass_Out_sink_In_ARVALID),
	.m_axi_pass_Out_sink_In_ARREADY(m_axi_pass_Out_sink_In_ARREADY),
	.m_axi_pass_Out_sink_In_ARADDR(m_axi_pass_Out_sink_In_ARADDR),
	.m_axi_pass_Out_sink_In_ARID(m_axi_pass_Out_sink_In_ARID),
	.m_axi_pass_Out_sink_In_ARLEN(m_axi_pass_Out_sink_In_ARLEN),
	.m_axi_pass_Out_sink_In_ARSIZE(m_axi_pass_Out_sink_In_ARSIZE),
	.m_axi_pass_Out_sink_In_ARBURST(m_axi_pass_Out_sink_In_ARBURST),
	.m_axi_pass_Out_sink_In_ARLOCK(m_axi_pass_Out_sink_In_ARLOCK),
	.m_axi_pass_Out_sink_In_ARCACHE(m_axi_pass_Out_sink_In_ARCACHE),
	.m_axi_pass_Out_sink_In_ARPROT(m_axi_pass_Out_sink_In_ARPROT),
	.m_axi_pass_Out_sink_In_ARQOS(m_axi_pass_Out_sink_In_ARQOS),
	.m_axi_pass_Out_sink_In_ARREGION(m_axi_pass_Out_sink_In_ARREGION),
	.m_axi_pass_Out_sink_In_ARUSER(m_axi_pass_Out_sink_In_ARUSER),
	.m_axi_pass_Out_sink_In_RVALID(m_axi_pass_Out_sink_In_RVALID),
	.m_axi_pass_Out_sink_In_RREADY(m_axi_pass_Out_sink_In_RREADY),
	.m_axi_pass_Out_sink_In_RDATA(m_axi_pass_Out_sink_In_RDATA),
	.m_axi_pass_Out_sink_In_RLAST(m_axi_pass_Out_sink_In_RLAST),
	.m_axi_pass_Out_sink_In_RID(m_axi_pass_Out_sink_In_RID),
	.m_axi_pass_Out_sink_In_RUSER(m_axi_pass_Out_sink_In_RUSER),
	.m_axi_pass_Out_sink_In_RRESP(m_axi_pass_Out_sink_In_RRESP),
	.m_axi_pass_Out_sink_In_BVALID(m_axi_pass_Out_sink_In_BVALID),
	.m_axi_pass_Out_sink_In_BREADY(m_axi_pass_Out_sink_In_BREADY),
	.m_axi_pass_Out_sink_In_BRESP(m_axi_pass_Out_sink_In_BRESP),
	.m_axi_pass_Out_sink_In_BID(m_axi_pass_Out_sink_In_BID),
	.m_axi_pass_Out_sink_In_BUSER(m_axi_pass_Out_sink_In_BUSER),

	.pass_Out_sink_In_available_size(pass_Out_sink_In_available_size),
	.pass_Out_sink_In_size_V(pass_Out_sink_In_size_r),
	.pass_Out_sink_In_buffer_V(pass_Out_sink_In_buffer),
	.fifo_count(pass_Out_sink_In_fifo_count_reg),
	.pass_Out_sink_In_V_dout(pass_Out_sink_In_dout),
	.pass_Out_sink_In_V_empty_n(pass_Out_sink_In_empty_n),
	.pass_Out_sink_In_V_read(pass_Out_sink_In_read),
	.pass_Out_sink_In_offset_V_dout(pass_Out_sink_In_offset_dout),
	.pass_Out_sink_In_offset_V_empty_n(pass_Out_sink_In_offset_empty_n),
	.pass_Out_sink_In_offset_V_read(pass_Out_sink_In_offset_read)
);

// --- offset FIFO
FIFO #(
	.MEM_STYLE("auto"),
	.DATA_WIDTH(64),
	.ADDR_WIDTH(1)
) pass_Out_sink_In_offset_fifo(
	.clk(ap_clk),
	.reset_n(ap_rst_n),
	.if_full_n(pass_Out_sink_In_offset_full_n),
	.if_write(pass_Out_sink_In_offset_write),
	.if_din(pass_Out_sink_In_offset_din),

	.if_empty_n(pass_Out_sink_In_offset_empty_n),
	.if_read(pass_Out_sink_In_offset_read),
	.if_dout(pass_Out_sink_In_offset_dout),

	.peek(),
	.count(),
	.size() 
);

// -- Offset fifo (initialization) logic
assign pass_Out_sink_In_offset_write = ap_start;
assign pass_Out_sink_In_offset_din = pass_Out_sink_In_offset;


endmodule
