/*
 * 
 *
 * 
 *
 *
 * 
 * 
 * 
 */

package com.example.issueJgit;

import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import com.example.issueJgit.model.AppConstants;
import com.example.issueJgit.model.TestModel;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class IssueExemple1 implements FieldSetMapper<TestModel> {

    @Override
    public TestModel mapFieldSet(FieldSet fieldSet) {
    	TestModel testModel = new TestModel();

        testModel.setCategory(fieldSet.readString(AppConstants.CATEGORY_CODE).trim());
        testModel.setStatus("0");
        testModel.setRegion(fieldSet.readString(AppConstants.REGION_CODE).trim());
        testModel.setType(fieldSet.readString(AppConstants.TYPE_CODE).trim());

        testModel.setFullName(fieldSet.readString(AppConstants.CATEGORY_NAME) +
                " - " +
                fieldSet.readString(AppConstants.TYPE_NAME) +
                " - " +
                fieldSet.readString(AppConstants.REGION_NAME));

        return testModel;
    }
}