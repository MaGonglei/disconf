package com.baidu.disconf2.client.scan.inner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.MethodParameterScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baidu.disconf2.client.common.annotations.DisconfActiveBackupService;
import com.baidu.disconf2.client.common.annotations.DisconfFile;
import com.baidu.disconf2.client.common.annotations.DisconfFileItem;
import com.baidu.disconf2.client.common.annotations.DisconfItem;
import com.baidu.disconf2.client.common.annotations.DisconfUpdateService;
import com.baidu.disconf2.client.common.constants.Constants;
import com.baidu.disconf2.client.common.inter.IDisconfUpdate;
import com.baidu.disconf2.utils.SpringContextUtil;
import com.google.common.base.Predicate;

/**
 * 
 * 扫描注解，并且进行分析整合数据
 * 
 * @author liaoqiqi
 * @version 2014-6-6
 */
public class ScanPack {

    protected static final Logger LOGGER = LoggerFactory
            .getLogger(ScanPack.class);

    /**
     * 通过扫描，获取反射对象
     * 
     * @param packName
     * @return
     */
    private static Reflections getReflection(String packName) {

        Predicate<String> filter = new FilterBuilder().includePackage(
                Constants.DISCONF_PACK_NAME).includePackage(packName);

        //
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .filterInputsBy(filter)
                .setScanners(new SubTypesScanner().filterResultsBy(filter),
                        new TypeAnnotationsScanner().filterResultsBy(filter),
                        new FieldAnnotationsScanner().filterResultsBy(filter),
                        new MethodAnnotationsScanner().filterResultsBy(filter),
                        new MethodParameterScanner())
                .setUrls(ClasspathHelper.forPackage(packName)));

        return reflections;
    }

    /**
     * 扫描想要的类
     * 
     * @return
     */
    public static ScanModel scan(String packName) {

        // 基本信息
        ScanModel scanModel = scanBasicInfo(packName);

        // 分析
        analysis(scanModel);

        return scanModel;
    }

    /**
     * 分析出一些关系 出来
     * 
     * @param scanModel
     */
    private static void analysis(ScanModel scanModel) {

        //
        // 分析出配置文件MAP
        //
        analysis4DisconfFile(scanModel);

        //
        // 分析出更新操作的相关配置文件内容
        //
        analysis4DisconfUpdate(scanModel);
    }

    /**
     * 分析出更新操作的相关配置文件内容
     */
    private static void analysis4DisconfUpdate(ScanModel scanModel) {

        Map<String, List<IDisconfUpdate>> inverseMap = new HashMap<String, List<IDisconfUpdate>>();
        Set<Class<?>> disconfUpdateServiceSet = scanModel
                .getDisconfUpdateService();
        for (Class<?> disconfUpdateServiceClass : disconfUpdateServiceSet) {

            DisconfUpdateService disconfUpdateService = disconfUpdateServiceClass
                    .getAnnotation(DisconfUpdateService.class);
            List<String> keysList = Arrays.asList(disconfUpdateService.keys());

            //
            // 校验是否有继承正确,是否继承IDisconfUpdate
            //
            if (!ScanVerify.hasIDisconfUpdate(disconfUpdateServiceClass)) {
                continue;
            }

            //
            // 回调函数需要实例化出来,这里
            // 非Spring直接New
            // Spring要GetBean
            //
            String beanName = getSpringServiceName(disconfUpdateServiceClass);

            IDisconfUpdate iDisconfUpdate;

            try {

                if (beanName != null) {

                    // Spring方式
                    iDisconfUpdate = getSpringBean(beanName);
                    if (iDisconfUpdate == null) {
                        continue;
                    }

                } else {

                    // 非Spring方式
                    iDisconfUpdate = (IDisconfUpdate) disconfUpdateServiceClass
                            .newInstance();
                }

            } catch (Exception e) {

                LOGGER.error("Your class "
                        + disconfUpdateServiceClass.toString()
                        + " cannot new instance. " + e.toString());
                continue;
            }

            // 反索引
            for (String key : keysList) {
                List<IDisconfUpdate> serviceList = null;
                if (inverseMap.containsKey(key)) {
                    inverseMap.get(key).add(iDisconfUpdate);
                } else {
                    serviceList = new ArrayList<IDisconfUpdate>();
                    serviceList.add(iDisconfUpdate);
                    inverseMap.put(key, serviceList);
                }
            }
        }
        scanModel.setDisconfUpdateServiceInverseIndexMap(inverseMap);
    }

    /**
     * 获取Spring Bean
     * 
     * @return
     */
    private static IDisconfUpdate getSpringBean(String beanName) {

        if (SpringContextUtil.getApplicationContext() == null) {
            LOGGER.error("Spring Context is null. Cannot autowire " + beanName);
            return null;
        }

        // spring 方式
        return (IDisconfUpdate) SpringContextUtil.getBean(beanName);
    }

    /**
     * 获取Spring service bean name,如果是非Spring，则返回null
     * 
     * @return
     */
    private static String getSpringServiceName(
            Class<?> disconfUpdateServiceClass) {

        Service serviceAnnotation = disconfUpdateServiceClass
                .getAnnotation(Service.class);
        if (serviceAnnotation == null) {
            return null;
        }

        String name = serviceAnnotation.value();
        if (name.isEmpty()) {
            name = disconfUpdateServiceClass.getSimpleName();
            name = name.substring(0, 1).toLowerCase() + name.substring(1);
        }

        return name;
    }

    /**
     * 分析出配置文件与配置文件中的Field的Method的MAP
     * 
     * @param scanModel
     */
    private static void analysis4DisconfFile(ScanModel scanModel) {

        Map<Class<?>, Set<Method>> disconfFileItemMap = new HashMap<Class<?>, Set<Method>>();

        //
        // 配置文件MAP
        //
        Set<Class<?>> classdata = scanModel.getDisconfFileClassSet();
        for (Class<?> classFile : classdata) {
            disconfFileItemMap.put(classFile, new HashSet<Method>());
        }

        //
        // 将配置文件与配置文件中的配置项进行关联
        //
        Set<Method> af1 = scanModel.getDisconfFileItemMethodSet();
        for (Method method : af1) {

            Class<?> thisClass = method.getDeclaringClass();

            if (disconfFileItemMap.containsKey(thisClass)) {
                Set<Method> fieldSet = disconfFileItemMap.get(thisClass);
                fieldSet.add(method);
                disconfFileItemMap.put(thisClass, fieldSet);

            } else {

                LOGGER.error("cannot find CLASS ANNOTATION "
                        + DisconfFile.class.getName()
                        + " for disconf file item: " + method.toString());
            }
        }

        //
        // 最后的校验
        //
        for (Class<?> classFile : disconfFileItemMap.keySet()) {

            // 校验是否所有配置文件都含有配置
            if (disconfFileItemMap.get(classFile).isEmpty()) {
                LOGGER.warn("disconf file hasn't any items: "
                        + classFile.getName());
                disconfFileItemMap.remove(classFile);
            }

            // 校验配置文件类型是否合适(目前只支持.properties类型)
            DisconfFile disconfFile = (DisconfFile) classFile
                    .getAnnotation(DisconfFile.class);
            boolean fileTypeRight = ScanVerify
                    .isDisconfFileTypeRight(disconfFile);
            if (!fileTypeRight) {
                disconfFileItemMap.remove(classFile);
            }
        }

        // 设置
        scanModel.setDisconfFileItemMap(disconfFileItemMap);
    }

    /**
     * 扫描基本信息
     * 
     * @return
     */
    private static ScanModel scanBasicInfo(String packName) {

        ScanModel scanModel = new ScanModel();

        //
        // 扫描对象
        //
        Reflections reflections = getReflection(packName);
        scanModel.setReflections(reflections);

        //
        // 获取DisconfFile
        //
        Set<Class<?>> classdata = reflections
                .getTypesAnnotatedWith(DisconfFile.class);
        scanModel.setDisconfFileClassSet(classdata);

        //
        // 获取DisconfFileItem
        //
        Set<Method> af1 = reflections
                .getMethodsAnnotatedWith(DisconfFileItem.class);
        scanModel.setDisconfFileItemMethodSet(af1);

        //
        // 获取DisconfItem
        //
        af1 = reflections.getMethodsAnnotatedWith(DisconfItem.class);
        scanModel.setDisconfItemMethodSet(af1);

        //
        // 获取DisconfActiveBackupService
        //
        classdata = reflections
                .getTypesAnnotatedWith(DisconfActiveBackupService.class);
        scanModel.setDisconfActiveBackupServiceClassSet(classdata);

        //
        // 获取DisconfActiveBackupService
        //
        classdata = reflections
                .getTypesAnnotatedWith(DisconfUpdateService.class);
        scanModel.setDisconfUpdateService(classdata);

        return scanModel;
    }

}