package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import org.json.simple.JSONObject;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

public class Main {
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static JSONObject jsonObject = new JSONObject();
    private static OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private static List<Map<String, Object>> conDatas;
    private static List<JSONObject> conJson;
    public static void main(String[] args) throws JsonProcessingException {
        String cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/json";
        String jsonString = getJsonString(cmd, true);
        int count;
        conDatas = objectMapper.readValue(jsonString, new TypeReference<List<Map<String, Object>>>() {});
        //System.out.println(conDatas);

        System.out.println("--------------------Monitoring--------------------");
        getHostinfo();

        count = 0;
        for(Map<String, Object> data : conDatas){
            System.out.println("------------Container"+count+"------------");
            getContainerinfo(data);
            System.out.println("----------------------------------");
            count++;
        }

    }

    private static void getHostinfo(){
        System.out.println("running_container : "+conDatas.size());
        jsonObject.put("running_container", conDatas.size());
        System.out.println("whole_cpucore : "+(Runtime.getRuntime().availableProcessors()/2*100)+"%");
        jsonObject.put("whole_cpucore", Runtime.getRuntime().availableProcessors()/2*100);
        long mem = operatingSystemMXBean.getTotalPhysicalMemorySize();
        System.out.println("whole_mem : "+mem+" bytes");
    }

    private static void getContainerinfo(Map<String, Object> data) throws JsonProcessingException {
        String conId = data.get("Id").toString();
        String conName = data.get("Names").toString();
        conName = conName.substring(2, conName.length()-1);
        String cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/"+conName+"/stats";
        String statString = getJsonString(cmd, false);
        Map<String, Object> cpustats;

        Map<String, Object> statJson = objectMapper.readValue(statString, new TypeReference<Map<String, Object>>() {});

        Map<String, Object> networkInfo = (Map<String, Object>) data.get("NetworkSettings");
        networkInfo = (Map<String, Object>) networkInfo.get("Networks");
        networkInfo = (Map<String, Object>) networkInfo.get("bridge");
        cpustats = (Map<String, Object>) statJson.get("cpu_stats");

        JSONObject conJsonObject = new JSONObject();

        System.out.println("IPAddress : "+networkInfo.get("IPAddress").toString());
        conJsonObject.put("ip_address", networkInfo.get("IPAddress").toString());

        System.out.println("Names : "+conName);
        conJsonObject.put("name", conName);

        System.out.println("Process ID : " +conId);
        conJsonObject.put("process_id", conId);

        System.out.println("CPU : "+cpustats.get("online_cpus").toString());
        conJsonObject.put("cpu", cpustats.get("online_cpus").toString());

        Map<String,Object> memory = (Map<String,Object>)statJson.get("memory_stats");
        System.out.println("Available Memory : "+memory.get("limit"));
        conJsonObject.put("available_memory", memory.get("limit"));




    }

    private static String getJsonString(String cmd, boolean mode){
        String s;
        String jsonString = "";
        Process p;

        try {
            p = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((s = br.readLine()) != null && mode){
                jsonString += s;
            }
            if(!mode){
                jsonString = s;
            }
            //System.out.println(jsonString);

            p.destroy();
        } catch (IOException e) {
            System.out.println("IOException occured");
        }
        return jsonString;
    }
}
