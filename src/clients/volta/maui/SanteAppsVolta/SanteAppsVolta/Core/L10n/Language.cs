namespace Volta.Core.L10n
{
    public record class Language
    {
        public required string Name { get; set; }
        public required string NativeName { get; set; }
        public required FlowDirection FlowDirection { get; set; }
    }
}
