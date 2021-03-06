package cn.batchfile.elasticsql.plugin;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import cn.batchfile.elasticsql.elasticsearch.Result;
import cn.batchfile.elasticsql.elasticsearch.StatementExecutor;
import cn.batchfile.elasticsql.exceptions.ExecuteException;

import com.github.mpjct.jmpjct.Engine;
import com.github.mpjct.jmpjct.JMP;
import com.github.mpjct.jmpjct.mysql.proto.Com_Initdb;
import com.github.mpjct.jmpjct.mysql.proto.Com_Query;
import com.github.mpjct.jmpjct.mysql.proto.ERR;
import com.github.mpjct.jmpjct.mysql.proto.Flags;
import com.github.mpjct.jmpjct.mysql.proto.Handshake;
import com.github.mpjct.jmpjct.mysql.proto.HandshakeResponse;
import com.github.mpjct.jmpjct.mysql.proto.OK;
import com.github.mpjct.jmpjct.mysql.proto.Packet;
import com.github.mpjct.jmpjct.mysql.proto.ResultSet;
import com.github.mpjct.jmpjct.plugin.Base;

public class ElasticsearchProxy extends Base {
	public Logger logger = Logger.getLogger("Plugin.ElasticsearchCover");
	private StatementExecutor statementExecutor;
	private static AtomicInteger _connectionId = new AtomicInteger(0);
	private int connectionId;

	public void init(Engine context) throws IOException {
		
		//connect to elasticsearch cluster
		statementExecutor = new StatementExecutor();
		String http_address = JMP.config.getProperty("elasticsearch.http");
		String transport_address = JMP.config.getProperty("elasticsearch.transport");
		statementExecutor.connect(http_address, transport_address);
		
		//connectionId increase
		connectionId = _connectionId.addAndGet(1);
	}
	
	/**
	 * compose handshake package
	 */
	public void read_handshake(Engine context) {
		this.logger.trace("read_handshake");
		context.handshake = new Handshake();
		context.handshake.authPluginDataLength = 21;
		context.handshake.authPluginName = "mysql_native_password";
		context.handshake.capabilityFlags = 4160716815L;
		context.handshake.challenge1 = "j@=RcyZ4";
		context.handshake.challenge2 = "7T%Xj/GZn<!, ";
		context.handshake.serverVersion = "5.5.28";
		context.handshake.characterSet = 8;
		context.handshake.connectionId = connectionId;
		context.handshake.protocolVersion = 10;
		context.handshake.sequenceId = 0;
		context.handshake.statusFlags = Flags.SERVER_STATUS_AUTOCOMMIT;
		
		ResultSet.characterSet = context.handshake.characterSet;
		context.sequenceId = context.handshake.sequenceId;
		context.statusFlags = 0;
		context.clear_buffer();
		context.buffer.add(context.handshake.toPacket());
	}

	/**
	 * send handshake package to client
	 */
    public void send_handshake(Engine context) throws IOException {
        this.logger.trace("send_handshake");
        Packet.write(context.clientOut, context.buffer);
        context.clear_buffer();
    }
    
    /**
     * read auth package from client
     */
    public void read_auth(Engine context) throws IOException {
        this.logger.trace("read_auth");
        byte[] packet = Packet.read_packet(context.clientIn);
        context.buffer.add(packet);
        
        context.authReply = HandshakeResponse.loadFromPacket(packet);
        
        if (!context.authReply.hasCapabilityFlag(Flags.CLIENT_PROTOCOL_41)) {
            this.logger.fatal("We do not support Protocols under 4.1");
            context.halt();
            return;
        }
        
        context.authReply.removeCapabilityFlag(Flags.CLIENT_COMPRESS);
        context.authReply.removeCapabilityFlag(Flags.CLIENT_SSL);
        context.authReply.removeCapabilityFlag(Flags.CLIENT_LOCAL_FILES);
        
        context.schema = context.authReply.schema;
    }
    
    public void send_auth(Engine context) throws IOException {
    }
    
	/**
	 * compose auth result package
	 */
    public void read_auth_result(Engine context) throws IOException {
        this.logger.trace("read_auth_result");
        OK ok = new OK();
        ok.affectedRows = 0;
        ok.lastInsertId= 0;
        ok.sequenceId = 2;
        ok.statusFlags = Flags.SERVER_STATUS_AUTOCOMMIT;
        ok.warnings = 0;
        context.clear_buffer();;
        context.buffer.add(ok.toPacket());
    }
    
    /**
     * send auth result package to client
     */
    public void send_auth_result(Engine context) throws IOException {
        this.logger.trace("read_auth_result");
        Packet.write(context.clientOut, context.buffer);
        context.clear_buffer();
    }
    
    /**
     * read query sql from client
     */
    public void read_query(Engine context) throws IOException {
        this.logger.trace("read_query");
        context.bufferResultSet = false;
        
        byte[] packet = Packet.read_packet(context.clientIn);
        context.buffer.add(packet);
        
        context.sequenceId = Packet.getSequenceId(packet);
        this.logger.trace("Client sequenceId: "+context.sequenceId);
        
        switch (Packet.getType(packet)) {
            case Flags.COM_QUIT:
                this.logger.trace("COM_QUIT");
                context.halt();
                break;
            
            // Extract out the new default schema
            case Flags.COM_INIT_DB:
                this.logger.trace("COM_INIT_DB");
                context.schema = Com_Initdb.loadFromPacket(packet).schema;
                break;
            
            // Query
            case Flags.COM_QUERY:
                this.logger.trace("COM_QUERY");
                context.query = Com_Query.loadFromPacket(packet).query;
                break;
            
            default:
                break;
        }
    }

    public void send_query(Engine context) throws IOException {
    }
    
    /**
     * compose query result 
     */
    public void read_query_result(Engine context) throws IOException {
    	this.logger.trace("read_query_result");
    	context.sequenceId ++;
    	String sql = context.query;
    	//logger.debug("-> " + sql);
    	
		context.clear_buffer();
    	try {
    		Result result = statementExecutor.execute(sql);
    		
    		if (result.resultSet != null) {
	            context.buffer = result.resultSet.toPackets();
    		} else {
	    		OK ok = new OK();
	    		ok.affectedRows = result.affectedRows;
	    		ok.lastInsertId = result.lastInsertId;
	    		ok.sequenceId = context.sequenceId;
	    		ok.statusFlags = Flags.SERVER_STATUS_AUTOCOMMIT;
	    		ok.warnings = result.warnings;
	    		context.buffer.add(ok.toPacket());
    		}
    	} catch (ExecuteException e) {
    		ERR err = new ERR();
    		err.errorCode = e.getCode();
    		err.errorMessage = e.getMessage();
    		err.sequenceId = context.sequenceId;
    		err.sqlState = e.getSqlState();
    		context.buffer.add(err.toPacket());
    	} catch (Exception e) {
    		ERR err = new ERR();
    		err.errorCode = 1051;
    		err.errorMessage = e.getMessage();
    		err.sequenceId = context.sequenceId;
    		err.sqlState = StringUtils.EMPTY;
    		context.buffer.add(err.toPacket());
    	}
    }
    
    /**
     * send query to client
     */
    public void send_query_result(Engine context) throws IOException {
        this.logger.trace("send_query_result");
        Packet.write(context.clientOut, context.buffer);
        context.clear_buffer();
    }
    
    /**
     * cleanup
     */
    public void cleanup(Engine context) {
    	statementExecutor = null;
    }

}
