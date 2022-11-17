package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;
import java.util.Map;

public class Main {
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static JSONObject jsonObeject = new JSONObject();
    private static OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    private static MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    public static void main(String[] args) throws JsonProcessingException {
        String s;
        String jsonString = "";
        Process p;
        try {
            String cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/json";
            p = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((s = br.readLine()) != null){
                jsonString += s;
            }
            //System.out.println(jsonString);

            p.waitFor();
            System.out.println("exit : " + p.exitValue());
            p.destroy();
        } catch (IOException e) {
            System.out.println("IOException occured");
        } catch (InterruptedException e) {
            System.out.println("InterruptedException occured");
        }

        List<Map<String, Object>> conDatas = objectMapper.readValue(jsonString, new TypeReference<List<Map<String, Object>>>() {});
        System.out.println("running_container : "+conDatas.size());
        jsonObeject.append("running_container", conDatas.size());
        System.out.println("whole_cpucore : "+Runtime.getRuntime().availableProcessors()/2);
        jsonObeject.put("whole_cpucore", Runtime.getRuntime().availableProcessors()/2);
        System.out.println("whole_cpu_useage : "+operatingSystemMXBean.getSystemLoadAverage()*100+"%");
        jsonObeject.append("whole_cpu_useage", operatingSystemMXBean.getSystemLoadAverage()*100);
        System.out.println("whole_mem : "+((double)Runtime.getRuntime().totalMemory()/1024/1024));
        long mem = memoryMXBean.getHeapMemoryUsage().getCommitted() + memoryMXBean.getNonHeapMemoryUsage().getCommitted();
        System.out.println("whole_mem : "+((double)mem/1024/1024)+" MB");

    }
}
