package ru.gravit.launchserver.binary.tasks.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import ru.gravit.launchserver.binary.tasks.LauncherBuildTask;

public final class TaskUtil {
	public static void addCounted(List<LauncherBuildTask> tasks, int count, Predicate<LauncherBuildTask> pred, LauncherBuildTask taskAdd) {
		List<Integer> indexes = new ArrayList<>();
		tasks.stream().filter(pred).forEach(e -> {
			indexes.add(tasks.indexOf(e)+count);
		});
		indexes.forEach(e -> {
			tasks.add(e, taskAdd);
		});
	}
	
	public static void replaceCounted(List<LauncherBuildTask> tasks, int count, Predicate<LauncherBuildTask> pred, LauncherBuildTask taskRep) {
		List<Integer> indexes = new ArrayList<>();
		tasks.stream().filter(pred).forEach(e -> {
			indexes.add(tasks.indexOf(e)+count);
		});
		indexes.forEach(e -> {
			tasks.set(e, taskRep);
		});
	}
	
	public static void addPre(List<LauncherBuildTask> tasks, Predicate<LauncherBuildTask> pred, LauncherBuildTask taskAdd) {
		addCounted(tasks, -1, pred, taskAdd);
	}
	
	public static void add(List<LauncherBuildTask> tasks, Predicate<LauncherBuildTask> pred, LauncherBuildTask taskAdd) {
		addCounted(tasks, 0, pred, taskAdd);
	}
	
	public static void addAfter(List<LauncherBuildTask> tasks, Predicate<LauncherBuildTask> pred, LauncherBuildTask taskAdd) {
		addCounted(tasks, 1, pred, taskAdd);
	}
	
	public static void replacePre(List<LauncherBuildTask> tasks, Predicate<LauncherBuildTask> pred, LauncherBuildTask taskRep) {
		replaceCounted(tasks, -1, pred, taskRep);
	}
	
	public static void replace(List<LauncherBuildTask> tasks, Predicate<LauncherBuildTask> pred, LauncherBuildTask taskRep) {
		replaceCounted(tasks, 0, pred, taskRep);
	}
	
	public static void replaceAfter(List<LauncherBuildTask> tasks, Predicate<LauncherBuildTask> pred, LauncherBuildTask taskRep) {
		replaceCounted(tasks, 1, pred, taskRep);
	}
	
	public static <T extends LauncherBuildTask> List<T> getTaskByClass(List<LauncherBuildTask> tasks, Class<T> taskClass) {
		return tasks.stream().filter(taskClass::isInstance).map(taskClass::cast).collect(Collectors.toList());
	}
	
	private TaskUtil() {
	}
}
