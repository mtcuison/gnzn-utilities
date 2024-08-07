package org.guanzon.gnzn.utilities;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.guanzon.appdriver.base.GRider;
import org.guanzon.gnzn.utilities.lib.hcm.PEF_Notification;
import org.json.simple.JSONObject;

public class NotifyPEF {
    public static void main(String[] args) {
        String path;
        if(System.getProperty("os.name").toLowerCase().contains("win")){
            path = "D:/GGC_Maven_Systems";
        }
        else{
            path = "/srv/GGC_Maven_Systems";
        }
        System.setProperty("sys.default.path.config", path);
        
        try {
            Properties po_props = new Properties();
            po_props.load(new FileInputStream(path + "/config/cas.properties"));
            
            GRider instance = null;
                    
            if (po_props.getProperty("developer.mode").equals("1")){
                instance = new GRider("gRider");
        
                if (!instance.logUser("gRider", "M001000001")){
                    System.err.println(instance.getErrMsg());
                    System.exit(1);
                }
            } else {
                System.err.println("Unable to log user.");
                System.exit(1);
            }
            
            PEF_Notification trans = new PEF_Notification(instance);
            
            JSONObject loJSON;

            loJSON = trans.SendNotifications();
            
            if (((String) loJSON.get("result")).equals("success")){
                System.out.println((String) loJSON.get("message"));
            } else {
                System.err.println((String) loJSON.get("message"));
                System.exit(1);
            }
        } catch (IOException e) {
            System.exit(1);
        }
        
        System.exit(0);
    }
}
