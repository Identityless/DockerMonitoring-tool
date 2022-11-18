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

        Map<String, Object> statJson = objectMapper.readValue(statString, new TypeReference<Map<String, Object>>() {});

        Map<String, Object> networkInfo = (Map<String, Object>) data.get("NetworkSettings");
        networkInfo = (Map<String, Object>) networkInfo.get("Networks");
        networkInfo = (Map<String, Object>) networkInfo.get("bridge");
        Map<String, Object> cpu_stats = (Map<String, Object>) statJson.get("cpu_stats");

        JSONObject conJsonObject = new JSONObject();

        System.out.println("IPAddress : "+networkInfo.get("IPAddress").toString());
        conJsonObject.put("ip_address", networkInfo.get("IPAddress").toString());

        System.out.println("Names : "+conName);
        conJsonObject.put("name", conName);

        System.out.println("Process ID : " +conId);
        conJsonObject.put("process_id", conId);

        int online_cpus = Integer.parseInt(cpu_stats.get("online_cpus").toString());
        System.out.println("CPU : "+online_cpus);
        conJsonObject.put("cpu", online_cpus);


        Map<String, Object> cpu_usage = (Map<String, Object>) cpu_stats.get("cpu_usage");
        Map<String, Object> precpu_stats = (Map<String, Object>) statJson.get("precpu_stats");
        Map<String, Object> precpu_usage = (Map<String, Object>) precpu_stats.get("cpu_usage");

        long cpu_delta = Long.parseLong(cpu_usage.get("total_usage").toString())-Long.parseLong(precpu_usage.get("total_usage").toString());
        long system_cpu_usage = Long.parseLong(cpu_stats.get("system_cpu_usage").toString());
        System.out.println(precpu_stats);
        long system_precpu_usage = Long.parseLong(precpu_stats.get("system_cpu_usage").toString()); // 않이 이거 외 않되?
        long system_cpu_delta = system_cpu_usage - system_precpu_usage;
        double cpu_usage_percent = ((double)cpu_delta/system_cpu_delta)*online_cpus*100.0;

        System.out.println("CPU Usage % : "+cpu_usage_percent);
        conJsonObject.put("cpu_usage%", cpu_usage_percent);


//        cpu_delta = cpu_stats.cpu_usage.total_usage - precpu_stats.cpu_usage.total_usage
//        system_cpu_delta = cpu_stats.system_cpu_usage - precpu_stats.system_cpu_usage
//        number_cpus = lenght(cpu_stats.cpu_usage.percpu_usage) or cpu_stats.online_cpus
//        CPU usage % = (cpu_delta / system_cpu_delta) * number_cpus * 100.0


        Map<String,Object> memory_stats = (Map<String,Object>)statJson.get("memory_stats");
        Map<String,Object> stats = (Map<String, Object>) memory_stats.get("stats");
        long used_memory = Long.parseLong(memory_stats.get("usage").toString())-Long.parseLong(stats.get("cache").toString()); //memory_stats.usage - memory_stats.stats.cache
        long available_memory = Long.parseLong(memory_stats.get("limit").toString())/8;
        System.out.println("Available Memory : "+available_memory+" bytes");
        conJsonObject.put("available_memory", available_memory);

        System.out.println("Used Memory :"+used_memory+" bytes");
        conJsonObject.put("used_momory", used_memory);

        System.out.println("Used Memory % : "+(((double)used_memory/available_memory)*100)+" %");
        conJsonObject.put("used_memory_%", (((double)used_memory/available_memory)*100));



//        used_memory = memory_stats.usage - memory_stats.stats.cache
//        available_memory = memory_stats.limit
//        Memory usage % = (used_memory / available_memory) * 100.0



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
