package org.guanzon.gnzn.utilities.lib.cp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.opencsv.CSVWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Properties;
import org.guanzon.appdriver.base.GRider;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Bolttech {
    private final String UPLOAD = System.getProperty("sys.default.path.config") + "/temp/Bolttech/upload/";
    private final String SUCCESS = System.getProperty("sys.default.path.config") + "/temp/Bolttech/success/";
    private final String FAILED = System.getProperty("sys.default.path.config") + "/temp/Bolttech/failed/";
    
    GRider _instance;
    
    public Bolttech(GRider foValue){
        _instance = foValue;
    }
    
    public JSONObject NewTransaction(){
        JSONObject loJSON = new JSONObject();
        
        String lsSQL;
        
        //get transactions that is not yet extracted
        lsSQL = getSQ_Master();
        
        ResultSet loRS = _instance.executeQuery(lsSQL);
        
        try {
            ResultSet loDetail;
            JSONObject loJSONDet;
            
            while (loRS.next()){
                lsSQL = MiscUtil.addCondition(getSQ_Detail(), "a.sTransNox = " + SQLUtil.toSQL(loRS.getString("sTransNox")));
                
                loDetail = _instance.executeQuery(lsSQL);
                
                if (loDetail.next()){
                    loJSONDet = new JSONObject();
                    
                    for (int lnCtr = 1; lnCtr <= loDetail.getMetaData().getColumnCount(); lnCtr++){
                        loJSONDet.put(loDetail.getMetaData().getColumnLabel(lnCtr), loDetail.getObject(lnCtr));
                    }
                    
                    lsSQL = "INSERT INTO CP_SO_Insurance SET" +
                            "  sTransNox = " + SQLUtil.toSQL(MiscUtil.getNextCode("CP_SO_Insurance", "sTransNox", true, _instance.getConnection(), _instance.getBranchCode())) +
                            ", dTransact = " + SQLUtil.toSQL(_instance.getServerDate()) +
                            ", sSourceNo = " + SQLUtil.toSQL(loRS.getString("sTransNox")) +
                            ", sStockIDx = " + SQLUtil.toSQL(loRS.getString("sStockIDx")) +
                            ", sPayloadx = " + SQLUtil.toSQL(loJSONDet.toJSONString()) +
                            ", sModified = " + SQLUtil.toSQL(_instance.getUserID());
                    
                    if (_instance.executeUpdate(lsSQL) <= 0){
                        loJSON.put("result", "error");
                        loJSON.put("message", _instance.getMessage() + "; " + _instance.getErrMsg());
                        return loJSON;
                    }
                }
            }
        } catch (SQLException e) {
            loJSON.put("result", "error");
            loJSON.put("message", e.getMessage());
            return loJSON;
        }
        
        loJSON.put("result", "success");
        loJSON.put("message", "Transactions exported successfully.");
        
        return loJSON;
    }
    
    public JSONObject CreateCSV(){
        JSONObject loJSON = new JSONObject();
        JSONParser loParser = new JSONParser();
        
        String lsSQL = getSQ_Batch();
        
        ResultSet loRS = _instance.executeQuery(lsSQL);
        
        if (MiscUtil.RecordCount(loRS) <= 0){
            loJSON.put("result", "success");
            loJSON.put("message", "No data to extract.");
            return loJSON;
        }
        
        String lsTransNox = MiscUtil.getNextCode("Bolttech", "sBatchNox", true, _instance.getConnection(), _instance.getBranchCode());
        String loFilename = UPLOAD + lsTransNox + ".csv";
        
        try (CSVWriter writer = new CSVWriter(new FileWriter(loFilename))) {
            int lnCtr = 0;
            
            while(loRS.next()){
                lsSQL = "INSERT INTO Bolttech SET" + 
                        "  sBatchNox = " + SQLUtil.toSQL(lsTransNox) +
                        ", dCreatedx = " + SQLUtil.toSQL(SQLUtil.dateFormat(_instance.getServerDate(), SQLUtil.FORMAT_TIMESTAMP)) +
                        ", cTranStat = '0'";
                
                if (_instance.executeUpdate(lsSQL) <= 0) {
                    loJSON.put("result", "error");
                    loJSON.put("message", _instance.getMessage() + "; " + _instance.getErrMsg());
                    return loJSON;
                }
                
                loJSON = (JSONObject) loParser.parse(loRS.getString("sPayloadx"));
                
                Iterator<?> keys = loJSON.keySet().iterator();
                
                if (lnCtr == 0){
                    // Write header
                    String [] header = new String[loJSON.size()];
                    
                    int i = 0;
                    while (keys.hasNext()) {
                        header[i++] = (String) keys.next();
                    }
                    
                    writer.writeNext(header);
                    lnCtr++;
                }
                
                // Write details
                String [] row = new String[loJSON.size()];
                
                int i = 0;
                for (Object key : loJSON.keySet()) {
                    row[i++] = loJSON.get(key).toString();
                }
                writer.writeNext(row);
                
                lsSQL = "UPDATE CP_SO_Insurance SET" +
                            "  cTranStat = '1'" +
                            ", sBatchNox = " + SQLUtil.toSQL(lsTransNox) +
                        " WHERE sTransNox = " + SQLUtil.toSQL(loRS.getString("sTransNox"));
                
                if (_instance.executeUpdate(lsSQL) <= 0) {
                    loJSON.put("result", "error");
                    loJSON.put("message", _instance.getMessage() + "; " + _instance.getErrMsg());
                    return loJSON;
                }
            }
        } catch (IOException | SQLException | ParseException e) {
            loJSON = new JSONObject();
            loJSON.put("result", "error");
            loJSON.put("message", e.getMessage());
            return loJSON;
        }
        
        loJSON.put("result", "success");
        loJSON.put("message", "Transactions exported successfully.");
        return loJSON;
    }
    
    public JSONObject UploadFile(){
        JSONObject loJSON = new JSONObject();
        
        try {
            loadConfig();
            
            Session session = null;
            ChannelSftp channelSftp = null;
            
            //connect to bolttech server
            JSch jsch = new JSch();
            jsch.addIdentity(System.getProperty("bolttech.pkey"));
            session = jsch.getSession(System.getProperty("bolttech.user"), 
                                            System.getProperty("bolttech.host"), 
                                            Integer.parseInt(System.getProperty("bolttech.port")));
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
            //end - connect to bolttech server
            
            //get the unsent transactions
            String lsSQL = "SELECT sBatchNox, cTranStat" +
                            " FROM Bolttech" +
                            " WHERE cTranStat IN ('0', '3')";
            
            ResultSet loRS = _instance.executeQuery(lsSQL);
            
            while (loRS.next()){
                String lsSRC;
                
                if (loRS.getString("cTranStat").equals("0")){
                    lsSRC = UPLOAD;
                } else{
                    lsSRC = FAILED;
                }
                
                try (FileInputStream fis = new FileInputStream(lsSRC + loRS.getString("sBatchNox") + ".csv")) {
                    channelSftp.put(fis, System.getProperty("bolttech.rdir") + loRS.getString("sBatchNox") + ".csv");
                
                    //update the transaction status to sent
                    lsSQL = "UPDATE Bolttech SET cTranStat = '1' WHERE sBatchNox = " + SQLUtil.toSQL(loRS.getString("sBatchNox"));
                    if (_instance.executeUpdate(lsSQL) <= 0) {
                        loJSON.put("result", "error");
                        loJSON.put("message", _instance.getMessage() + "; " + _instance.getErrMsg());
                        return loJSON;
                    }
                    
                    //move the file to success folder
                    Path sourcePath = Paths.get(lsSRC + loRS.getString("sBatchNox") + ".csv");
                    Path destinationPath = Paths.get(SUCCESS + loRS.getString("sBatchNox") + ".csv");
                    Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (SftpException ex) {
                    //update the transaction status to failed
                    lsSQL = "UPDATE Bolttech SET cTranStat = '3' WHERE sBatchNox = " + SQLUtil.toSQL(loRS.getString("sBatchNox"));
                    if (_instance.executeUpdate(lsSQL) <= 0) {
                        loJSON.put("result", "error");
                        loJSON.put("message", _instance.getMessage() + "; " + _instance.getErrMsg());
                        return loJSON;
                    }
                    
                    //move the file to unsent folder
                    Path sourcePath = Paths.get(lsSRC + loRS.getString("sBatchNox") + ".csv");
                    Path destinationPath = Paths.get(FAILED + loRS.getString("sBatchNox") + ".csv");
                    Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    loJSON = new JSONObject();
                    loJSON.put("result", "error");
                    loJSON.put("message", ex.getMessage());
                    return loJSON;
                }
            }
        } catch (JSchException | IOException | SQLException e) {
            loJSON = new JSONObject();
            loJSON.put("result", "error");
            loJSON.put("message", e.getMessage());
            return loJSON;
        }
        
        loJSON.put("result", "success");
        loJSON.put("message", "Files uploaded successfully.");
        return loJSON;
    }
    
    private void loadConfig() throws IOException{
        Properties props = new Properties();
        props.load(new FileInputStream(System.getProperty("sys.default.path.config") + "/config/maven.properties"));
        
        System.setProperty("bolttech.port", props.getProperty("bolttech.port"));
        System.setProperty("bolttech.host", props.getProperty("bolttech.host"));
        System.setProperty("bolttech.user", props.getProperty("bolttech.user"));
        System.setProperty("bolttech.rdir", props.getProperty("bolttech.rdir"));
        System.setProperty("bolttech.pkey", System.getProperty("sys.default.path.config") + "/config/" + props.getProperty("bolttech.pkey"));
    }
    
    private String getSQ_Master(){
        return "SELECT" +
                    "  a.sTransNox" +
                    ", b.sStockIDx" +
                    ", c.sCategID1" +
                    ", e.sTransNox xTransNox" +
                " FROM CP_SO_Master a" +
                    " LEFT JOIN CP_SO_Insurance e ON a.sTransNox = e.sSourceNo" +
                    ", CP_SO_Detail b" +
                        " LEFT JOIN CP_Inventory c ON b.sStockIDx = c.sStockIDx" +
                        " LEFT JOIN Category d ON c.sCategID1 = d.sCategrID" +
                " WHERE a.sTransNox = b.sTransNox" +
                    " AND a.cTranStat IN ('3', '7')" +
                    " AND a.dTransact >= '2024-06-27'" +
                " HAVING c.sCategID1 = 'C001052'" +
                    " AND xTransNox IS NULL";
    }
    
    private String getSQ_Detail(){
        return "SELECT" + 
                    "  a.sSalesInv CLIENT_TRANS_NO" + 
                    ", DATE_FORMAT(a.dTransact,'%d/%m/%Y') CONTRACT_SOLD_DATE" + 
                    ", f.sCategrNm PRODUCT_NAME" + 
                    ", TRIM(CONCAT(g.sFrstName, ' ', g.sLastName )) CUST_NAME" + 
                    ", '' CUST_ID" + 
                    ", g.sMobileNo CUST_MOBILE_NO" + 
                    ", g.sEmailAdd CUST_EMAIL" + 
                    ", TRIM(CONCAT(g.sAddressx, ' ', j.sTownName, ', ', h.sProvName)) CUST_ADDRESS" + 
                    ", '' CUST_CITY" + 
                    ", d.sBranchCd STORE_CODE" + 
                    ", d.sBranchNm STORE_NAME" + 
                    ", '' STORE_ADDRESS" + 
                    ", i.sTownName STORE_CITY" + 
                    ", a.sSalesman SALES_REP_ID" + 
                    ", TRIM(CONCAT(k.sFrstName, ' ', k.sLastName)) SALES_REP_NAME" + 
                    ", c.nPurchase DEVICE_RRP" + 
                    ", e.sCategrNm DEVICE_TYPE" + 
                    ", l.sBrandNme DEVICE_MAKE" + 
                    ", m.sModelNme DEVICE_MODEL" + 
                    ", n.sColorNme COLOR" + 
                    ", o.sSerialNo IMEI" + 
                    ", e.sCategrNm NAME_GOODS_TYPE" + 
                    ", DATE_FORMAT(a.dTransact,'%d/%m/%Y') DTIME_START" + 
                    ", '' DTIME_END" + 
                    ", c.nPurchase DEVICE_VALUE_SUM_COVERED" + 
                    ", '' CREATION_DATE" + 
                    ", o.sSerialNo SERIALNO" + 
                    ", 'PHGUANZRETNA01' PARTNER_ID" + 
                    ", 'MBG' VALUE_ADDED_SERVICES" + 
                    ", '' DWH_UNIQUE_KEY" + 
                " FROM CP_SO_Master a" + 
                        " LEFT JOIN Client_Master g ON a.sClientID = g.sClientID" + 
                        " LEFT JOIN TownCity j ON g.sTownIDxx = j.sTownIDxx" + 
                        " LEFT JOIN Province h ON j.sProvIDxx = h.sProvIDxx" + 
                        " LEFT JOIN Salesman k ON a.sSalesman = k.sEmployID" + 
                    ", CP_SO_Detail b" + 
                        " LEFT JOIN CP_Inventory_Serial o ON b.sSerialID = o.sSerialID" + 
                    ", CP_Inventory c" + 
                        " LEFT JOIN Category e ON c.sCategID1 = e.sCategrID" + 
                        " LEFT JOIN Category f ON c.sCategID2 =  f.sCategrID" + 
                        " LEFT JOIN CP_Brand l ON c.sBrandIDx = l.sBrandIDx" + 
                        " LEFT JOIN CP_Model m ON c.sModelIDx = m.sModelIDx" + 
                        " LEFT JOIN Color n ON c.sColorIDx = n.sColorIDx" + 
                    ", Branch d" + 
                        " LEFT JOIN TownCity i ON d.sTownIDxx = i.sTownIDxx" + 
                " WHERE a.sTransNox = b.sTransNox" + 
                    " AND b.sStockIDx = c.sStockIDx" + 
                    " AND LEFT(a.sTransNox,4) = d.sBranchCd" + 
                    " AND a.cTranStat IN ('3', '7')" +
                " HAVING PRODUCT_NAME = 'Units'";
    }
    
    private String getSQ_Batch(){
        return "SELECT *" +
                " FROM CP_SO_Insurance" +
                " WHERE cTranStat = '0'" +
                    " AND sBatchNox IS NULL";
    }
}
