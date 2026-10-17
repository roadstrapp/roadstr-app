class Permission {
  const Permission({
    required this.type,
    this.kind,
  });

  final String type;
  final int? kind;

  Map<String, dynamic> toJson() {
    return {
      "type": type,
      "int": kind,
    };
  }
}
