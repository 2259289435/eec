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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.ttzero.excel.Print.println;
import static org.ttzero.excel.entity.WorkbookTest.getRandomString;

/**
 * @author guanquan.wang at 2019-05-08 17:04
 */
public class SharedStringTableTest {
    private SharedStringTable sst;

    @Before public void before() throws IOException {
        sst = new SharedStringTable();
    }

    @After public void after() throws IOException {
        sst.close();
    }

    @Test public void testPutChar() throws IOException {
        int n = sst.push('a');

        assert n == 0;
        assert sst.size() == 1;

        int index = sst.find('a');
        assert index == 0;

        index = sst.find('z');
        assert index == -1;
    }

    @Test public void testPutString() throws IOException {
        int n = sst.push("abc");
        assert n == 0;
        assert sst.size() == 1;

        sst.push("ab");

        int index = sst.find("ab");
        assert index == 1;

        index = sst.find("abc");
        assert index == 0;

        index = sst.find("acc");
        assert index == -1;

        index = sst.find("abd");
        assert index == -1;

        index = sst.find("123");
        assert index == -1;

        index = sst.push('a');
        assert index == 2;

        index = sst.push('z');
        assert index == 3;

        index = sst.push('阿');
        assert index == 4;

        assert sst.find('z') == 3;

        assert sst.find('阿') == 4;

        for (String s : sst)
            println(s);
    }

    @Test public void testPush() throws IOException {
        int size = 10_000;
        for (int i = 0; i < size; i++) {
            sst.push(getRandomString());
        }

//        sst.forEach(Print::println);

//        int i = 0;
//        for (String aSst : sst) {
//            print(i++);
//            print(' ');
//            println(aSst);
//        }
        assert sst.size() == size;
    }

    @Test public void testFind() throws IOException {
        int size = 1_000_000;
        for (int i = 0; i < size; i++) {
            sst.push(getRandomString());
        }
        sst.push("abcdefghijklmn");

        int index;

        long start = System.currentTimeMillis();
//        index = sst.find('a');
//        println(index);
//        assert index == -1;

//        index = sst.find(getRandomString());
//        println(index);

        index = sst.find("abcdefghijklmn");
        println(index);
        assert index == size;

        println(System.currentTimeMillis() - start);
    }

    @Test public void testNull() throws IOException {
        int n;
        sst.push("a");
        n = sst.push(null);

        assert n == 1;
        assert sst.find(null) == n;
    }

//    @Test public void testX() throws IOException {
//        Path path = Paths.get("C:\\Users\\wangguanquan\\AppData\\Local\\Temp\\+2698958064069533007.sst");
//        try (SharedStringTable sst = new SharedStringTable(path)) {
//
//            println(sst.size());
//
//            for (String v : sst) {
//                println(v);
//            }
//        }
//    }
}
