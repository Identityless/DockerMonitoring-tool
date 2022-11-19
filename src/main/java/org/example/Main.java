package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import org.json.simple.JSONObject;


import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static JSONObject jsonObject = new JSONObject();
    private static OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private static List<Map<String, Object>> conDatas;
    private static List<JSONObject> conJson;
    private static NetworkInfo[] networkInfos = new NetworkInfo[100];
    private static double cpuUsageofAllContainers;
    private static long momoryUsageofAllContainers;
    private static String path = "/home/identityless/result.txt";
    public static void main(String[] args) throws IOException {
        String cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/json";
        PrintWriter printWriter = new PrintWriter(new FileWriter(path, true));
        String jsonString = getJsonString(cmd, true);
        int count;
        List<JSONObject> conjsonObjectList = null;
        conDatas = objectMapper.readValue(jsonString, new TypeReference<List<Map<String, Object>>>() {});
        //System.out.println(conDatas);

        while(true) {
            conjsonObjectList = new ArrayList<>();
            System.out.println("--------------------Monitoring Start--------------------");
            getHostinfo();

            count = 0;
            cpuUsageofAllContainers = 0;
            momoryUsageofAllContainers = 0;
            for (Map<String, Object> data : conDatas) {
                System.out.println("------------Container" + count + "------------");
                conjsonObjectList.add(getContainerinfo(data, count));

                System.out.println("----------------------------------");
                count++;
            }
            jsonObject.put("containers", conjsonObjectList);
            System.out.println("CPU Available : "+((Runtime.getRuntime().availableProcessors()/2*100)-cpuUsageofAllContainers)+" %");
            jsonObject.put("cpu_available", ((Runtime.getRuntime().availableProcessors()/2*100)-cpuUsageofAllContainers));
            System.out.println("Memory Available : "+(operatingSystemMXBean.getTotalPhysicalMemorySize()-momoryUsageofAllContainers)+" bytes");
            jsonObject.put("memory_available", (operatingSystemMXBean.getTotalPhysicalMemorySize()-momoryUsageofAllContainers));
            System.out.println("-------------------Momitoring End-------------------\n");
            System.out.println(jsonObject.toJSONString());
            printWriter.println(jsonObject.toJSONString());
            printWriter.flush();
            printWriter.close();
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

    private static JSONObject getContainerinfo(Map<String, Object> data, int count) throws JsonProcessingException {
        String conId = data.get("Id").toString();
        String conName = data.get("Names").toString();
        conName = conName.substring(2, conName.length()-1);
        String cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/"+conName+"/stats";
        String statString = getJsonString(cmd, false);
        Map<String, Object> statJson = objectMapper.readValue(statString, new TypeReference<Map<String, Object>>() {});
        cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/"+conName+"/top";
        statString = getJsonString(cmd, false);
        Map<String, Object> topJson = objectMapper.readValue(statString, new TypeReference<Map<String, Object>>() {});
        List<List<Integer>> topList = (List<List<Integer>>) topJson.get("Processes");
        List<Integer> topRaw = topList.get(0);


        Map<String, Object> networkInfo = (Map<String, Object>) data.get("NetworkSettings");
        networkInfo = (Map<String, Object>) networkInfo.get("Networks");
        networkInfo = (Map<String, Object>) networkInfo.get("bridge");
        Map<String, Object> cpu_stats = (Map<String, Object>) statJson.get("cpu_stats");

        JSONObject conJsonObject = new JSONObject();

        System.out.println("PID : "+topRaw.get(1));
        conJsonObject.put("pid", topRaw.get(1));

        System.out.println("IPAddress : "+networkInfo.get("IPAddress").toString());
        conJsonObject.put("ip_address", networkInfo.get("IPAddress").toString());

        System.out.println("Names : "+conName);
        conJsonObject.put("name", conName);

        System.out.println("Container ID : " +conId);
        conJsonObject.put("container_id", conId);

        int online_cpus = Integer.parseInt(cpu_stats.get("online_cpus").toString());
        System.out.println("CPU : "+online_cpus);
        conJsonObject.put("cpu", online_cpus);


        Map<String, Object> cpu_usage = (Map<String, Object>) cpu_stats.get("cpu_usage");
        Map<String, Object> precpu_stats = (Map<String, Object>) statJson.get("precpu_stats");
        Map<String, Object> precpu_usage = (Map<String, Object>) precpu_stats.get("cpu_usage");

        long cpu_delta = Long.parseLong(cpu_usage.get("total_usage").toString())-Long.parseLong(precpu_usage.get("total_usage").toString());
        long total_usage = Long.parseLong(cpu_usage.get("total_usage").toString());
        long pretotal_usage = Long.parseLong(precpu_usage.get("total_usage").toString());
        long system_cpu_usage = Long.parseLong(cpu_stats.get("system_cpu_usage").toString());
        //System.out.println(precpu_stats);
        //long system_precpu_usage = Long.parseLong(precpu_stats.get("system_cpu_usage").toString()); // 않이 이거 외 않되? -> 스펙에는 나와있는 필드가 실제로는 없다?!?!
        //long system_cpu_delta = system_cpu_usage - system_precpu_usage;
        double cpu_usage_percent = ((double)total_usage-pretotal_usage)*online_cpus*100.0/system_cpu_usage;

        System.out.println("CPU Usage % : "+cpu_usage_percent+" %");
        conJsonObject.put("cpu_usage%", cpu_usage_percent);
        cpuUsageofAllContainers += cpu_usage_percent;


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
        momoryUsageofAllContainers += used_memory;


//        used_memory = memory_stats.usage - memory_stats.stats.cache
//        available_memory = memory_stats.limit
//        Memory usage % = (used_memory / available_memory) * 100.0

        Map<String, Object> network = (Map<String, Object>)statJson.get("networks");
        Map<String, Object> eth0 = (Map<String, Object>) network.get("eth0");
        NetworkInfo netInfo;


        calRxTx(eth0, count);
        conJsonObject.put("rx_bytes", networkInfos[count].getRxbytes());
        conJsonObject.put("rx_packets", networkInfos[count].getRxpackets());
        conJsonObject.put("tx_bytes", networkInfos[count].getTxbytes());
        conJsonObject.put("tx_packets", networkInfos[count].getTxpackets());

        return conJsonObject;


    }

    private static void calRxTx(Map<String, Object> eth0, int count) {
        if(networkInfos[count] == null) {
            networkInfos[count] = new NetworkInfo();
            networkInfos[count].setRxbytes(Long.parseLong(eth0.get("rx_bytes").toString()));
            networkInfos[count].setRxpackets(Long.parseLong(eth0.get("rx_packets").toString()));
            networkInfos[count].setTxbytes(Long.parseLong(eth0.get("tx_bytes").toString()));
            networkInfos[count].setTxpackets(Long.parseLong(eth0.get("tx_packets").toString()));
            System.out.println("Rx bytes(packet) : initializing......");
            System.out.println("Tx bytes(packet) : initializing......");
        }
        else {
            networkInfos[count].setRxbytes(Long.parseLong(eth0.get("rx_bytes").toString())-networkInfos[count].getRxbytes());
            networkInfos[count].setRxpackets(Long.parseLong(eth0.get("rx_packets").toString())-networkInfos[count].getRxpackets());
            networkInfos[count].setTxbytes(Long.parseLong(eth0.get("tx_bytes").toString())-networkInfos[count].getTxbytes());
            networkInfos[count].setTxpackets(Long.parseLong(eth0.get("tx_packets").toString())-networkInfos[count].getTxpackets());
            System.out.println("Rx bytes(packet) : "+networkInfos[count].getRxbytes()+"("+networkInfos[count].getRxpackets()+")");
            System.out.println("Tx bytes(packet) : "+networkInfos[count].getTxbytes()+"("+networkInfos[count].getTxpackets()+")");
        }
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

    static class NetworkInfo {
        private long Rxbytes;
        private long Txbytes;
        private long Rxpackets;
        private long Txpackets;

        public NetworkInfo() {
            Rxbytes = -1;
            Txbytes = -1;
            Rxpackets = -1;
            Txpackets = -1;
        }

        public long getRxbytes() {
            return Rxbytes;
        }

        public void setRxbytes(long rxbytes) {
            Rxbytes = rxbytes;
        }

        public long getTxbytes() {
            return Txbytes;
        }

        public void setTxbytes(long txbytes) {
            Txbytes = txbytes;
        }

        public long getRxpackets() {
            return Rxpackets;
        }

        public void setRxpackets(long rxpackets) {
            Rxpackets = rxpackets;
        }

        public long getTxpackets() {
            return Txpackets;
        }

        public void setTxpackets(long txpackets) {
            Txpackets = txpackets;
        }
    }
}
