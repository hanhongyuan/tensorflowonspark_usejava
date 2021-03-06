package org.apache.spark.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.deploy.yarn.Client;
import org.apache.spark.deploy.yarn.ClientArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * 运行Spark引擎
 */
public class SparkUtils {
    private final static Logger logger = LoggerFactory.getLogger(SparkUtils.class);
    /**
     * 运行 Spark任务
     * @return
     */
    public static int runSparkJob(String hadoopConfigFile,
                                  String sparkConfigFile,String ... args ) throws IOException {

        if(!hasResourceJar(hadoopConfigFile) || !hasResourceJar(sparkConfigFile)){
            logger.info("资源文件hadoop config:{} 或 spark config:{}不存在！",
                    new Object[]{hadoopConfigFile,sparkConfigFile});
            return -1;
        }else{
            logger.info("资源文件存在！");
        }
        Configuration conf = new Configuration();
        logger.info("updating hadoop resources ...");
        updateConf(conf,hadoopConfigFile);

        String appId = runSpark(conf,sparkConfigFile,args);

        logger.info("appId:{}",appId);
        return 0;
    }

    public static String runSpark(Configuration conf ,String configFile,String[] args) {
        StringBuffer buff = new StringBuffer();
        for(String arg:args){
            buff.append(arg).append(",");
        }
        logger.info("runSpark args:"+buff.toString());
        ApplicationId appId = null;
        try {
            System.setProperty("SPARK_YARN_MODE", "true");
            SparkConf sparkConf = new SparkConf();
            logger.info("updating spark properties ...");
            updateConf(sparkConf,configFile);
//

            ClientArguments cArgs = new ClientArguments(args, sparkConf);

            Client client = new Client(cArgs, conf, sparkConf);
            // 调用Spark
            try{
                appId = client.submitApplication();
            }catch(Throwable e){
                e.printStackTrace();
                // 清空临时文件
                cleanupStagingDir(conf, appId);
                //  返回null
                return null;
            }
            return appId.toString();
        } catch (Exception e) {
            e.printStackTrace();
            // 清空临时文件
            cleanupStagingDir(conf ,appId);
            return null;
        }
    }
    public static void cleanupStagingDir(Configuration conf ,ApplicationId appId) {
        String appStagingDir = Client.SPARK_STAGING() + Path.SEPARATOR + appId.toString();

        try {
            Path stagingDirPath = new Path(appStagingDir);
            FileSystem fs = FileSystem.get(conf);
            if (fs.exists(stagingDirPath)) {
                logger.info("Deleting staging directory " + stagingDirPath);
                fs.delete(stagingDirPath, true);
            }
        } catch (IOException e) {
            logger.warn("Failed to cleanup staging dir " + appStagingDir, e);
        }
    }
    public static void cleanupStagingDir(Configuration conf ,String appId) {
        cleanupStagingDir(conf, ConverterUtils
                .toApplicationId(appId));
    }
    private static void updateConf(Configuration conf, String configFile) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(new File(configFile)));
        for(final String name: properties.stringPropertyNames()){
            conf.set(name,properties.getProperty(name));
        }
    }

    private static void updateConf(SparkConf conf,String configFile )throws IOException{
        Properties properties = new Properties();
        properties.load(new FileInputStream(new File(configFile)));
        for(final String name: properties.stringPropertyNames()) {
            conf.set(name,properties.getProperty(name));
        }
    }

    /**
     * 判断资源文件是否存在
     * @param  localJar
     * @return
     */
    private static boolean hasResourceJar(String localJar) {
        if(new File(localJar).exists())
            return true;
        return false;
    }

    public static void main(String[] args) throws IOException {
        test1();
    }

    /**
     * use hadoop-2.7.4 spark 1.5.1 apache
     * @throws IOException
     */
    private static void test1() throws IOException {
        String pyFileStr = "src/main/java/org/apache/spark/util/iris_c.py";
        File pyFile = new File(pyFileStr);
        System.out.println(pyFile.toURI().toString());
        String[] args = {
                "--class","org.apache.spark.deploy.PythonRunner",
                "--executor-memory","720M",
                "--executor-cores","1",
                "--driver-memory","512M",
                "/abc/local-job.jar",
                "--arg","/user/root/abc",
                "--arg","/user/root/tmp"
        };
        String hadoopConfigFile = "src/main/java/org/apache/spark/util/hadoop.properties";
        System.out.println(new File(".").getAbsoluteFile());
        String sparkConfigFile = "src/main/java/org/apache/spark/util/spark.properties";

        int t = runSparkJob(hadoopConfigFile, sparkConfigFile, args);
        System.out.println("result : " + t);

    }
}
