package com.salesforce.dva.argus.service.tsdb;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.TSDBEntity;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.salesforce.dva.argus.system.SystemException;
import org.kududb.ts.KuduTSClient;
import org.kududb.ts.KuduTSTable;
import org.kududb.ts.QueryResult;
import org.kududb.ts.TimeAndValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KuduService extends DefaultService implements TSDBService {
  protected Logger _logger = LoggerFactory.getLogger(getClass());

  private final KuduTSClient client;
  private KuduTSTable table;

  @Inject
  public KuduService(SystemConfiguration config, MonitorService monitorService) {
    super(config);
    client = KuduTSClient.create(Lists.newArrayList("locahost"));
    try {
      table = client.CreateTable("argus");
    } catch (Exception e) {
      _logger.info("Error creating table", e);
      try {
        table = client.OpenTable("argus");
      } catch (Exception e1) {
        e1.printStackTrace();
      }
    }
  }

  @Override
  public void putMetrics(List<Metric> metrics) {
    for (Metric metric : metrics) {
      _logger.info("Writing metric " + metric);
      SortedMap<String, String> tags = new TreeMap<>(metric.getTags());
      String metricName = metric.getScope() + "." + metric.getMetric();
      _logger.info(" metric " + metricName);
      if (metric.getDisplayName() != null && !metric.getDisplayName().isEmpty()) {
        tags.put(TSDBEntity.ReservedField.DISPLAY_NAME.name(), metric.getDisplayName());
      }
      if (metric.getUnits() != null && !metric.getUnits().isEmpty()) {
        tags.put(TSDBEntity.ReservedField.UNITS.name(), metric.getUnits());
      }
      for (Map.Entry<Long, String> dp : metric.getDatapoints().entrySet()) {
        try {
          _logger.info(" writing " + dp.toString());
          table.writeMetric(metricName, tags, dp.getKey(), Double.parseDouble(dp.getValue()));
        } catch (Exception e) {
          throw new SystemException(e);
        }
      }
    }
    try {
      _logger.info(" flush ");
      table.flush();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
    Map<MetricQuery, List<Metric>> result = new HashMap<>();
    for(MetricQuery query : queries) {
      _logger.info("Running query " + query);
      String metricName = query.getScope() + "." + query.getMetric();
      _logger.info(" metric " + metricName);
      SortedMap<String, String> tags = new TreeMap<>(query.getTags());
      _logger.info(" tags " + tags);
      try {
        QueryResult qr = table.queryMetrics(query.getStartTimestamp(), query.getEndTimestamp(), metricName, tags);
        Metric resultMetric = new Metric(query.getScope(), query.getMetric());
        resultMetric.setTags(tags);
        resultMetric.setDisplayName(metricName);
        HashMap<Long, String> datapoints = new HashMap<>(qr.getDatapoints().size());
        _logger.info(" adding datapoints ");
        for (TimeAndValue tv : qr.getDatapoints()) {
          _logger.info(" adding datapoint " + tv.getTime() + " " + tv.getValue());
          datapoints.put(tv.getTime(), tv.getValue()+"");
        }
        resultMetric.addDatapoints(datapoints);
        _logger.info(" full metric " + resultMetric);
        result.put(query, Lists.newArrayList(resultMetric));
      } catch (Exception e) {
        throw new SystemException(e);
      }
    }
    _logger.info(" returning result size " + result.size());
    return result;
  }

  @Override
  public void putAnnotations(List<Annotation> annotations) {
    // TODO Auto-generated method stub

  }

  @Override
  public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String constructTSDBMetricName(String scope, String namespace) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getScopeFromTSDBMetric(String tsdbMetricName) {
    // TODO
    return null;
  }

  @Override
  public String getNamespaceFromTSDBMetric(String tsdbMetricName) {
    // TODO
    return null;
  }
}
