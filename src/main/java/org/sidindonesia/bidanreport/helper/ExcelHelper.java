package org.sidindonesia.bidanreport.helper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.sidindonesia.bidanreport.exception.ExcelWriteException;
import org.sidindonesia.bidanreport.util.CamelCaseUtil;
import org.sidindonesia.bidanreport.util.ReflectionsUtil;
import org.springframework.context.ApplicationContext;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExcelHelper {
	public static final String TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	public static ByteArrayInputStream allEntitiesToExcelSheets(ApplicationContext context) {

		try (Workbook workbook = new SXSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

			Set<Class<?>> entityClasses = ReflectionsUtil.getAllEntityClasses();

			entityClasses.stream().forEach(entityClass -> {
				Sheet sheet = workbook.createSheet(CamelCaseUtil.camelToSnake(entityClass.getSimpleName()));

				Row headerRow = sheet.createRow(0);

				Field[] fields = entityClass.getDeclaredFields();
				int headerCol = 0;
				for (Field field : fields) {
					Cell cell = headerRow.createCell(headerCol++);
					cell.setCellValue(CamelCaseUtil.camelToSnake(field.getName()));
				}

				Class<?> repositoryClass = ReflectionsUtil.getRepositoryClassOfEntity(entityClass);
				try {
					Object repositoryInstance = context.getBean(repositoryClass);
					Object invokeResult = repositoryClass.getMethod("findAll").invoke(repositoryInstance);
					List<?> result = (List<?>) invokeResult;

					AtomicInteger rowIdx = new AtomicInteger(0);
					result.stream().forEach(entry -> {
						Row contentRow = sheet.createRow(rowIdx.incrementAndGet());
						int contentCol = 0;
						for (Field field : fields) {
							Cell cell = contentRow.createCell(contentCol++);
							cell.setCellValue(field.getName() + contentCol);
							// TODO change this into get the field value
						}
					});
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
					throw new ExcelWriteException("Fail to import data to Excel file: " + e.getMessage());
				}
			});

			workbook.write(out);
			return new ByteArrayInputStream(out.toByteArray());
		} catch (IOException e) {
			throw new ExcelWriteException("Fail to import data to Excel file: " + e.getMessage());
		}
	}
}