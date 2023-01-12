/*
 * Copyright (c) 2017-2019, guanquan.wang@yandex.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ttzero.excel.entity;

import org.junit.Test;
import org.ttzero.excel.entity.style.Styles;
import org.ttzero.excel.processor.StyleProcessor;
import org.ttzero.excel.reader.ExcelReader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.IntStream;

import static org.ttzero.excel.reader.ExcelReaderTest.testResourceRoot;

/**
 * @author guanquan.wang at 2019-05-05 10:53
 */
public class TemplateTest extends WorkbookTest {
    private static final Styles DEFAULT_STYLES;
    private static final Map<String, Integer> DEFAULT_KV_STYLES;
    static{
        String url = "https://export-service.oss-cn-beijing.aliyuncs.com/export/render/styles.xlsx";
        try(ExcelReader reader = ExcelReader.read(new URL(url).openStream())){
            DEFAULT_STYLES = reader.getStyles();
            DEFAULT_KV_STYLES = reader.sheet(0)
                    .rows()
                    .flatMap(row ->
                            IntStream.range(row.getFirstColumnIndex(), row.getLastColumnIndex())
                                    .boxed()
                                    .map(i ->
                                            new AbstractMap.SimpleEntry<>(
                                                    row.getString(i) == null ? null : row.getString(i).trim()
                                                    , row.getCellStyle(i)
                                            )
                                    )
                    ).collect(HashMap::new, (m, o) -> m.put(o.getKey(), o.getValue()), HashMap::putAll);
        }catch (Throwable e){
            throw new RuntimeException(e);
        }
    }

    @Test public void testCopyStyle() throws IOException {
        Workbook workbook = new Workbook("复制样式").cancelOddFill().setAutoSize(true);
        workbook.setStyles(DEFAULT_STYLES);
        ListMapSheet sheet = new ListMapSheet("测试") {
            int max = 3;
            @Override
            protected List<Map<String, ?>> more() {
                if(max-- == 0){
                    return null;
                }
                List<Map<String, Object>> data = new ArrayList<>();
                for(int i = 0; i < 10; i++){
                    Map<String, Object> row = new HashMap<>();
                    row.put("a", UUID.randomUUID().toString());
                    row.put("b", UUID.randomUUID().toString());
                    row.put("c", UUID.randomUUID().toString());
                    row.put("d", UUID.randomUUID().toString());
                    row.put("e", UUID.randomUUID().toString());
                    row.put("f", UUID.randomUUID().toString());
                    row.put("g", UUID.randomUUID().toString());
                    if(max % 2 == 0){
                        row.put("分组行", null);
                    }else{
                        row.put("数据行", null);
                    }
                    data.add(row);
                }
                return new ArrayList<>(data);
            }
        };
        workbook.addSheet(sheet);
        Column[] columns = Arrays.asList(
                new Column("表头").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头", 0)).addSubColumn(
                        new Column("查询1").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头说明", 0)).addSubColumn(
                                new Column("a", "a").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("列名称", 0))
                        )
                )
                , new Column("表头").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头", 0)).addSubColumn(
                        new Column("查询1").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头说明", 0)).addSubColumn(
                                new Column("b", "b").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("列名称", 0))
                        )
                )
                , new Column("表头").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头", 0)).addSubColumn(
                        new Column("查询2").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头数值", 0)).addSubColumn(
                                new Column("c", "c").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("列名称", 0))
                        )
                )
                , new Column("表头").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头", 0)).addSubColumn(
                        new Column("查询2").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头数值", 0)).addSubColumn(
                                new Column("d", "d").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("列名称", 0))
                        )
                )
                , new Column("表头").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头", 0)).addSubColumn(
                        new Column("查询3").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头说明", 0)).addSubColumn(
                                new Column("e", "e").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("列名称", 0))
                        )
                )
                , new Column("表头").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头", 0)).addSubColumn(
                        new Column("查询3").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头说明", 0)).addSubColumn(
                                new Column("f", "f").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("列名称", 0))
                        )
                )
                , new Column("表头").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头", 0)).addSubColumn(
                        new Column("查询3").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("表头说明", 0)).addSubColumn(
                                new Column("g", "g").setHeaderStyle(DEFAULT_KV_STYLES.getOrDefault("列名称", 0))
                        )
                )
        ).toArray(new Column[0]);
        sheet.setColumns(columns);
        sheet.setStyleProcessor(new StyleProcessor<Map<String, ?>>() {
            int idx = 0;
            final int mod = columns.length;
            @Override
            public int build(Map<String, ?> o, int style, Styles sst) {
                int cur = idx % mod;
                Column column = columns[cur];
                style = 0;
                if(o.containsKey("数据行")){
                    style = DEFAULT_KV_STYLES.getOrDefault("表内居中", 0);
                }else if(o.containsKey("分组行")){
                    style = DEFAULT_KV_STYLES.getOrDefault("合计行", 0);
                }
                idx++;
                return style;
            }
        });
        workbook.writeTo(defaultTestPath);
    }

    @Test public void testTemplate() throws IOException {
        try (InputStream fis = Files.newInputStream(testResourceRoot().resolve("template.xlsx"))) {
            // Map data
            Map<String, Object> map = new HashMap<>();
            map.put("name", "guanquan.wang");
            map.put("score", 90);
            map.put("date", "2019-05-05");
            map.put("desc", "暑假");

            // java bean
//            BindEntity entity = new BindEntity();
//            entity.score = 67;
//            entity.name = "张三";
//            entity.date = new Date(System.currentTimeMillis());

            new Workbook("模板导出", author)
                .withTemplate(fis, map)
                .writeTo(defaultTestPath);
        }
    }
}
