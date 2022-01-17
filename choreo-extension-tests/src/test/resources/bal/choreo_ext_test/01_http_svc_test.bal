// Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/http;
import ballerina/observe;

service /test on new http:Listener(9091) {
    resource function get sum() returns string {
        ObservableAdderClass adder = new ObservableAdder(20, 33);
        var sum = adder.getSum();
        return "Sum: " + sum.toString();
    }

    resource function get loopWithLargeTags() returns string {
        int finalSum = 0;
        int i = 0;
        while i < 45 { // i added as a metric tag can be used to tune the number of metrics published
            int j = 0;
            while j < 25 { // i * j + 1 number of spans are generated in total
                ObservableAdderClass adder = new ObservableAdder(i, j);
                finalSum += adder.getSum();
                j += 1;
            }
            i += 1;
        }
        return "Sum: " + finalSum.toString();
    }

    resource function get loopWithLargeSpanCount() returns string {
        int finalSum = 0;
        int i = 0;
        while i < 2500 {
            finalSum += sum(21, 33);
            i += 1;
        }
        return "Sum: " + finalSum.toString();
    }
}

type ObservableAdderClass object {
    @observe:Observable
    function getSum() returns int;
};

class ObservableAdder {
    private int firstNumber;
    private int secondNumber;

    function init(int a, int b) {
        self.firstNumber = a;
        self.secondNumber = b;
    }

    function getSum() returns int {
        error? err = observe:addTagToMetrics("choreo.ext.test.sum.first_number", self.firstNumber.toString());
        if (err is error) {
            panic err;
        }
        err = observe:addTagToMetrics("choreo.ext.test.sum.large_string", LARGE_STRING);
        if (err is error) {
            panic err;
        }
        err = observe:addTagToSpan("choreo.ext.test.sum.large_string", LARGE_STRING);
        if (err is error) {
            panic err;
        }
        return self.firstNumber + self.secondNumber;
    }
}

@observe:Observable
function sum(int a, int b) returns int {
    return a + b;
}
