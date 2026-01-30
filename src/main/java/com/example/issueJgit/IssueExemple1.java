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

		String category = fieldSet.readString(AppConstants.CATEGORY_CODE).trim();
		String region = fieldSet.readString(AppConstants.REGION_CODE).trim();
		String type = fieldSet.readString(AppConstants.TYPE_CODE).trim();

		if (!category.isEmpty() || !region.isEmpty() || !type.isEmpty()) {

			testModel.setCategory(category);
			testModel.setStatus("0");
			testModel.setRegion(region);
			testModel.setType(type);

			testModel.setFullName(fieldSet.readString(AppConstants.CATEGORY_NAME) +
                    " - " +
                    fieldSet.readString(AppConstants.TYPE_NAME) +
                    " - " +
                    fieldSet.readString(AppConstants.REGION_NAME));
        }

        return testModel;
    }
}